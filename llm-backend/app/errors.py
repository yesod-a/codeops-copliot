"""Safe provider error classification shared by the API and reviewer."""

import json
import re
from typing import Any


_SECRET_PATTERNS = (
    re.compile(r"(?i)(authorization\s*[:=]\s*bearer\s+)[^\s,}]+"),
    re.compile(r"(?i)(api[_ -]?key\s*[:=]\s*)[^\s,}]+"),
    re.compile(r"\bsk-[A-Za-z0-9_-]+"),
)


def provider_status(error: BaseException) -> int | None:
    """Extract an HTTP status without depending on a provider SDK type."""
    for candidate in (getattr(error, "status_code", None), getattr(error, "status", None)):
        if isinstance(candidate, int) and 100 <= candidate <= 599:
            return candidate
    response = getattr(error, "response", None)
    candidate = getattr(response, "status_code", None)
    return candidate if isinstance(candidate, int) and 100 <= candidate <= 599 else None


def classify_error(error: BaseException) -> str:
    status = provider_status(error)
    text = str(error).lower()
    name = type(error).__name__.lower()
    if status == 429 or "rate limit" in text or "too many requests" in text:
        return "PROVIDER_RATE_LIMIT"
    if status is not None and status >= 500:
        return "PROVIDER_5XX"
    if "timeout" in name or "timed out" in text or "timeout" in text:
        return "PROVIDER_TIMEOUT"
    if "unavailable" in text or "connection" in text or "connect" in name:
        return "PROVIDER_UNAVAILABLE"
    if isinstance(error, (ValueError, json.JSONDecodeError)) or "json" in text:
        return "REVIEW_RESPONSE_INVALID"
    return "PROVIDER_ERROR"


def is_retryable(error: BaseException) -> bool:
    return classify_error(error) in {"PROVIDER_RATE_LIMIT", "PROVIDER_5XX", "PROVIDER_TIMEOUT", "PROVIDER_UNAVAILABLE"}


def safe_error_message(error: BaseException, limit: int = 240) -> str:
    value = " ".join(str(error).split()) or type(error).__name__
    for pattern in _SECRET_PATTERNS:
        value = pattern.sub(r"\1<redacted>" if pattern.groups else "<redacted>", value)
    return value[:limit]
