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
    message: str = Field(min_length=1, max_length=2_000)
    suggestion: str = Field(min_length=1, max_length=4_000)
    evidence: str = Field(default="", max_length=4_000)
    confidence: float = Field(ge=0, le=1)


class ReviewResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    findings: list[ReviewFinding] = Field(default_factory=list, max_length=100)


class AiReviewRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    repository: str = Field(min_length=1)
    title: str = Field(min_length=1)
    files: list[ReviewFile] = Field(min_length=1, max_length=100)
