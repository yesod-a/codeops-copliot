import json
import re
from typing import Any

from langchain_openai import ChatOpenAI
from langchain_core.messages import ToolMessage

from .config import Settings
from .context import read_repository_file
from .findings import normalize_findings
from .models import ReviewFile, ReviewFinding, ReviewResponse
from .prompts import build_messages, build_plan_messages
from .rules import resolve_rule
from .tools import build_review_tools


LARGE_CHANGE_CHARS = 12_000
MAX_TOOL_ROUNDS = 6
MAX_GROUP_FILES = 6
MAX_GROUP_CHARS = 50_000
MAX_SINGLE_FILE_CHARS = 30_000
MAX_GROUP_CONTEXT_CHARS = 30_000


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
        if self.model is None:
            raise RuntimeError("LLM review is disabled; set AI_ENABLED=true to enable it")

        findings: list[ReviewFinding] = []
        for group in group_review_files(files):
            plan = build_review_plan(group)
            group_chars = sum(len(file.content or "") for file in group)
            # Keep small multi-file groups on the single Main review call. OCR
            # only spends an extra round on genuinely large changes; otherwise
            # grouping would still double the latency for ordinary pushes.
            if group_chars >= LARGE_CHANGE_CHARS:
                plan_response = self._invoke_with_tools(build_plan_messages(
                    repository, title, [file.model_dump() for file in group]
                ), repository)
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
            response = self._invoke_with_tools(messages, repository)
            findings.extend(
                normalize_findings(
                    self._parse_response(getattr(response, "content", response)),
                    group,
                )
            )
        return findings

    def _invoke_with_tools(self, messages, repository: str):
        """Run a bounded tool-calling loop when the provider supports it."""
        tools = build_review_tools(repository)
        model = self.model
        if callable(getattr(model, "bind_tools", None)):
            model = model.bind_tools(tools)
        conversation = list(messages)
        tool_map = {tool.name: tool for tool in tools}
        for _ in range(MAX_TOOL_ROUNDS):
            response = model.invoke(conversation)
            calls = getattr(response, "tool_calls", None) or []
            if not calls:
                return response
            conversation.append(response)
            for call in calls:
                name = call.get("name")
                args = call.get("args") or {}
                tool = tool_map.get(name)
                if tool is None:
                    result = f"未知工具：{name}"
                else:
                    try:
                        result = tool.invoke(args)
                    except Exception as error:
                        result = f"工具调用失败：{error}"
                conversation.append(ToolMessage(
                    content=str(result),
                    tool_call_id=call.get("id", name or "tool"),
                ))
        raise ValueError("LLM tool-calling loop exceeded the maximum rounds")

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
