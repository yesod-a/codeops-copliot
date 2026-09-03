from typing import Protocol

from fastapi import FastAPI, HTTPException

from .config import Settings
from .models import AiReviewRequest, ReviewResponse
from .reviewer import AiReviewer


class Reviewer(Protocol):
    def review(self, repository: str, title: str, files: list):
        ...


def create_app(settings: Settings | None = None, reviewer: Reviewer | None = None) -> FastAPI:
    active_settings = settings or Settings()
    active_reviewer = reviewer or AiReviewer(active_settings)
    app = FastAPI(title="CodeOps LLM Backend", version="0.1.0")

    @app.get("/api/ai/health")
    def health():
        return {
            "status": "ready" if active_settings.ai_enabled else "disabled",
            "model": active_settings.ai_model,
        }

    @app.post("/api/ai/review", response_model=ReviewResponse)
    def review(request: AiReviewRequest):
        try:
            findings = active_reviewer.review(request.repository, request.title, request.files)
            return ReviewResponse(findings=findings)
        except Exception as error:
            raise HTTPException(status_code=503, detail="LLM provider is unavailable") from error

    return app


app = create_app()
