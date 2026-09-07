from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


Severity = Literal["CRITICAL", "HIGH", "MEDIUM", "LOW"]


class ReviewFile(BaseModel):
    model_config = ConfigDict(extra="forbid")

    path: str = Field(min_length=1)
    content: str = Field(default="", max_length=300_000)


class ReviewFinding(BaseModel):
    model_config = ConfigDict(extra="forbid")

    category: str = Field(min_length=1, max_length=64)
    severity: Severity
    file: str = Field(min_length=1)
    line: int = Field(ge=1)
    start_line: int | None = Field(default=None, ge=1)
    end_line: int | None = Field(default=None, ge=1)
    message: str = Field(min_length=1, max_length=2_000)
    suggestion: str = Field(min_length=1, max_length=4_000)
    evidence: str = Field(default="", max_length=4_000)
    confidence: float = Field(ge=0, le=1)


class ReviewResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    findings: list[ReviewFinding] = Field(default_factory=list, max_length=100)
    metrics: "ReviewMetrics | None" = None


class ToolCallMetric(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1, max_length=64)
    duration_ms: int = Field(ge=0)
    status: Literal["SUCCESS", "FAILED"]
    error_code: str | None = Field(default=None, max_length=64)


class ReviewMetrics(BaseModel):
    model_config = ConfigDict(extra="forbid")

    model: str = Field(min_length=1, max_length=120)
    duration_ms: int = Field(ge=0)
    input_tokens: int | None = Field(default=None, ge=0)
    output_tokens: int | None = Field(default=None, ge=0)
    total_tokens: int | None = Field(default=None, ge=0)
    estimated_cost: float | None = Field(default=None, ge=0)
    tool_calls: list[ToolCallMetric] = Field(default_factory=list, max_length=100)
    phase_durations_ms: dict[str, int] = Field(default_factory=dict)


class AiReviewRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    repository: str = Field(min_length=1)
    title: str = Field(min_length=1)
    files: list[ReviewFile] = Field(min_length=1, max_length=100)
    task_id: str | None = Field(default=None, max_length=36)
    group_number: int | None = Field(default=None, ge=1)
