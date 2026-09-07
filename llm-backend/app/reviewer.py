import json
import logging
import re
import time
from dataclasses import dataclass, field
from typing import Any

from langchain_openai import ChatOpenAI
from langchain_core.messages import ToolMessage

from .config import Settings
from .context import read_repository_file
from .errors import classify_error, is_retryable, safe_error_message
from .findings import normalize_findings
from .models import ReviewFile, ReviewFinding, ReviewMetrics, ReviewResponse, ToolCallMetric
from .prompts import build_messages, build_plan_messages
from .rules import resolve_rule
from .tools import build_review_tools


LARGE_CHANGE_CHARS = 12_000
MAX_TOOL_ROUNDS = 6
MAX_GROUP_FILES = 6
MAX_GROUP_CHARS = 50_000
MAX_SINGLE_FILE_CHARS = 30_000
MAX_GROUP_CONTEXT_CHARS = 30_000

logger = logging.getLogger("codeops.llm")


def group_review_files(files: list[ReviewFile]) -> list[list[ReviewFile]]:
    """Pack files into bounded review units, isolating oversized files."""
    groups: list[list[ReviewFile]] = []
    current: list[ReviewFile] = []
    current_chars = 0
    for file in files:
        size = len(file.content or "")
        if size > MAX_SINGLE_FILE_CHARS:
            if current:
                groups.append(current)
                current, current_chars = [], 0
            groups.append([file])
            continue
        if current and (len(current) >= MAX_GROUP_FILES or current_chars + size > MAX_GROUP_CHARS):
            groups.append(current)
            current, current_chars = [], 0
        current.append(file)
        current_chars += size
    if current:
        groups.append(current)
    return groups


def build_review_plan(files: list[ReviewFile]) -> str:
    total_chars = sum(len(file.content or "") for file in files)
    if total_chars < LARGE_CHANGE_CHARS:
        return "小变更：逐文件检查安全性、正确性、可维护性和测试覆盖。"
    paths = ", ".join(file.path for file in files[:20])
    return (
        f"大型变更（{len(files)} 个文件，约 {total_chars} 个字符）："
        f"先按文件边界检查，再关注跨文件契约、异常处理和测试缺口。涉及文件：{paths}"
    )


def build_group_context(repository: str, files: list[ReviewFile]) -> str:
    """Read bounded context for each group member under one shared cap."""
    sections: list[str] = []
    remaining = MAX_GROUP_CONTEXT_CHARS
    for file in files:
        if remaining <= 0:
            break
        content = read_repository_file(repository, file.path)
        if not content:
            continue
        section = f"文件：{file.path}\n{content}"
        sections.append(section[:remaining])
        remaining -= len(sections[-1])
    return "\n\n".join(sections)


def build_group_rule(repository: str, files: list[ReviewFile]) -> str:
    rules = []
    for file in files:
        rules.append(f"[{file.path}] {resolve_rule(repository, file.path)}")
    return "\n".join(rules)


class AiReviewer:
    def __init__(self, settings: Settings, model: Any | None = None):
        self.settings = settings
        self.model = model or self._create_model()

    def _create_model(self):
        if not self.settings.ai_enabled:
            return None
        if not self.settings.ai_api_key:
            raise RuntimeError("AI_API_KEY is required when AI_ENABLED=true")
        return ChatOpenAI(
            api_key=self.settings.ai_api_key,
            base_url=self.settings.ai_base_url,
            model=self.settings.ai_model,
            temperature=self.settings.ai_temperature,
            timeout=self.settings.ai_timeout_seconds,
        )

    def review(self, repository: str, title: str, files: list[ReviewFile]) -> list[ReviewFinding]:
        return self.review_with_metrics(repository, title, files).findings

    def review_with_metrics(self, repository: str, title: str, files: list[ReviewFile]) -> "ReviewRun":
        if self.model is None:
            raise RuntimeError("LLM review is disabled; set AI_ENABLED=true to enable it")

        findings: list[ReviewFinding] = []
        recorder = _MetricsRecorder(self.settings.ai_model)
        started = time.perf_counter()
        for group in group_review_files(files):
            plan = build_review_plan(group)
            group_chars = sum(len(file.content or "") for file in group)
            # Keep small multi-file groups on the single Main review call. OCR
            # only spends an extra round on genuinely large changes; otherwise
            # grouping would still double the latency for ordinary pushes.
            if group_chars >= LARGE_CHANGE_CHARS:
                plan_response = self._invoke_with_tools(build_plan_messages(
                    repository, title, [file.model_dump() for file in group]
                ), repository, phase="PLAN", recorder=recorder)
                generated_plan = getattr(plan_response, "content", plan_response)
                if isinstance(generated_plan, list):
                    generated_plan = "".join(str(part) for part in generated_plan)
                if generated_plan and str(generated_plan).strip():
                    plan = f"{plan}\n模型生成计划：\n{str(generated_plan).strip()[:6000]}"
            messages = build_messages(
                repository,
                title,
                [file.model_dump() for file in group],
                rule=build_group_rule(repository, group),
                context=build_group_context(repository, group),
                plan=plan,
            )
            response = self._invoke_with_tools(messages, repository, phase="REVIEW", recorder=recorder)
            findings.extend(
                normalize_findings(
                    self._parse_response(getattr(response, "content", response)),
                    group,
                )
            )
        elapsed_ms = max(0, round((time.perf_counter() - started) * 1000))
        pricing = self.settings.ai_model_pricing.get(self.settings.ai_model)
        return ReviewRun(findings=findings, metrics=recorder.build(
            elapsed_ms,
            pricing=pricing,
            fallback_input=self.settings.ai_input_cost_per_1k_tokens,
            fallback_output=self.settings.ai_output_cost_per_1k_tokens,
        ))

    def _invoke_with_tools(self, messages, repository: str, *, phase: str = "REVIEW",
                           recorder: "_MetricsRecorder | None" = None):
        """Run a bounded tool-calling loop when the provider supports it."""
        tools = build_review_tools(repository)
        model = self.model
        if callable(getattr(model, "bind_tools", None)):
            model = model.bind_tools(tools)
        conversation = list(messages)
        tool_map = {tool.name: tool for tool in tools}
        phase_started = time.perf_counter()
        for _ in range(MAX_TOOL_ROUNDS):
            response = self._invoke_provider(model, conversation, phase)
            if recorder is not None:
                recorder.add_usage(_extract_usage(response))
            calls = getattr(response, "tool_calls", None) or []
            if not calls:
                if recorder is not None:
                    recorder.add_phase(phase, phase_started)
                return response
            conversation.append(response)
            for call in calls:
                name = call.get("name")
                args = call.get("args") or {}
                tool = tool_map.get(name)
                if tool is None:
                    result = f"未知工具：{name}"
                    if recorder is not None:
                        recorder.add_tool(name or "unknown", 0, "FAILED", "UNKNOWN_TOOL")
                else:
                    tool_started = time.perf_counter()
                    try:
                        result = tool.invoke(args)
                        if recorder is not None:
                            recorder.add_tool(name, round((time.perf_counter() - tool_started) * 1000), "SUCCESS", None)
                    except Exception as error:
                        result = f"工具调用失败：{error}"
                        if recorder is not None:
                            recorder.add_tool(name, round((time.perf_counter() - tool_started) * 1000), "FAILED", "TOOL_ERROR")
                conversation.append(ToolMessage(
                    content=str(result),
                    tool_call_id=call.get("id", name or "tool"),
                ))
        if recorder is not None:
            recorder.add_phase(phase, phase_started)
        raise ValueError("LLM tool-calling loop exceeded the maximum rounds")

    def _invoke_provider(self, model, conversation, phase: str):
        retries = max(0, min(self.settings.ai_provider_retries, 5))
        backoff = max(0.0, min(self.settings.ai_retry_backoff_seconds, 30.0))
        for attempt in range(retries + 1):
            try:
                return model.invoke(conversation)
            except Exception as error:
                if attempt >= retries or not is_retryable(error):
                    raise
                delay = backoff * (2 ** attempt)
                logger.warning(
                    "provider_retry phase=%s attempt=%d/%d error_code=%s delay_seconds=%.1f error=%s",
                    phase, attempt + 1, retries, classify_error(error), delay, safe_error_message(error),
                )
                if delay:
                    time.sleep(delay)

    @staticmethod
    def _parse_response(content: Any) -> list[ReviewFinding]:
        if isinstance(content, list):
            content = "".join(
                part.get("text", "") if isinstance(part, dict) else str(part)
                for part in content
            )
        if not isinstance(content, str):
            raise ValueError("LLM response must be text JSON")

        cleaned = content.strip()
        fenced = re.fullmatch(r"```(?:json)?\s*(.*?)\s*```", cleaned, re.DOTALL | re.IGNORECASE)
        if fenced:
            cleaned = fenced.group(1).strip()
        try:
            payload = json.loads(cleaned)
        except json.JSONDecodeError as error:
            raise ValueError("LLM response is not valid JSON") from error
        return ReviewResponse.model_validate(payload).findings


@dataclass
class ReviewRun:
    findings: list[ReviewFinding]
    metrics: ReviewMetrics


@dataclass
class _MetricsRecorder:
    model: str
    input_tokens: int | None = None
    output_tokens: int | None = None
    total_tokens: int | None = None
    tool_calls: list[ToolCallMetric] = field(default_factory=list)
    phase_durations_ms: dict[str, int] = field(default_factory=dict)

    def add_usage(self, usage: tuple[int | None, int | None, int | None]):
        input_tokens, output_tokens, total_tokens = usage
        if input_tokens is not None:
            self.input_tokens = (self.input_tokens or 0) + input_tokens
        if output_tokens is not None:
            self.output_tokens = (self.output_tokens or 0) + output_tokens
        if total_tokens is not None:
            self.total_tokens = (self.total_tokens or 0) + total_tokens
        elif input_tokens is not None or output_tokens is not None:
            self.total_tokens = (self.total_tokens or 0) + (input_tokens or 0) + (output_tokens or 0)

    def add_phase(self, phase: str, started: float):
        self.phase_durations_ms[phase] = self.phase_durations_ms.get(phase, 0) + max(0, round((time.perf_counter() - started) * 1000))

    def add_tool(self, name: str, duration_ms: int, status: str, error_code: str | None):
        self.tool_calls.append(ToolCallMetric(name=name[:64], duration_ms=max(0, duration_ms), status=status, error_code=error_code))

    def build(self, duration_ms: int, pricing: dict[str, float] | None = None,
              fallback_input: float = 0.0, fallback_output: float = 0.0) -> ReviewMetrics:
        rates = pricing or {}
        input_rate = rates.get("input", fallback_input)
        output_rate = rates.get("output", fallback_output)
        cost = None
        if self.input_tokens is not None and self.output_tokens is not None and (input_rate or output_rate):
            cost = round((self.input_tokens / 1000) * input_rate + (self.output_tokens / 1000) * output_rate, 8)
        return ReviewMetrics(model=self.model, duration_ms=duration_ms, input_tokens=self.input_tokens,
                             output_tokens=self.output_tokens, total_tokens=self.total_tokens,
                             estimated_cost=cost, tool_calls=self.tool_calls,
                             phase_durations_ms=self.phase_durations_ms)


def _extract_usage(response: Any) -> tuple[int | None, int | None, int | None]:
    """Extract provider-neutral token counts from LangChain response metadata."""
    usage = getattr(response, "usage_metadata", None) or {}
    if not usage:
        metadata = getattr(response, "response_metadata", None) or {}
        usage = metadata.get("token_usage") or metadata.get("usage") or metadata
    def number(*keys):
        for key in keys:
            value = usage.get(key) if isinstance(usage, dict) else None
            if isinstance(value, (int, float)):
                return int(value)
        return None
    input_tokens = number("input_tokens", "prompt_tokens")
    output_tokens = number("output_tokens", "completion_tokens")
    total_tokens = number("total_tokens")
    return input_tokens, output_tokens, total_tokens
