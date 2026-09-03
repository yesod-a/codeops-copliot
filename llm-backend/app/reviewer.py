import json
import re
from typing import Any

from langchain_openai import ChatOpenAI

from .config import Settings
from .models import ReviewFile, ReviewFinding, ReviewResponse
from .prompts import build_messages


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

        messages = build_messages(repository, title, [file.model_dump() for file in files])
        response = self.model.invoke(messages)
        return self._parse_response(getattr(response, "content", response))

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

