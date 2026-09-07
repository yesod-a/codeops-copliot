import logging
import uuid
from typing import Protocol

from fastapi import FastAPI, HTTPException, Response

from .config import Settings
from .models import AiReviewRequest, ReviewResponse
from .reviewer import AiReviewer
from .errors import classify_error, provider_status, safe_error_message


logger = logging.getLogger("codeops.llm")


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
    def review(request: AiReviewRequest, response: Response):
        request_id = uuid.uuid4().hex[:16]
        file_sizes = [len(file.content or "") for file in request.files]
        total_chars = sum(file_sizes)
        try:
            review_with_metrics = getattr(active_reviewer, "review_with_metrics", None)
            if callable(review_with_metrics):
                run = review_with_metrics(request.repository, request.title, request.files)
                logger.info(
                    "review_succeeded request_id=%s task_id=%s group=%s files=%d chars=%d",
                    request_id, request.task_id or "-", request.group_number or "-", len(request.files), total_chars,
                )
                return ReviewResponse(findings=run.findings, metrics=run.metrics)
            findings = active_reviewer.review(request.repository, request.title, request.files)
            logger.info(
                "review_succeeded request_id=%s task_id=%s group=%s files=%d chars=%d",
                request_id, request.task_id or "-", request.group_number or "-", len(request.files), total_chars,
            )
            return ReviewResponse(findings=findings)
        except Exception as error:
            code = classify_error(error)
            status = provider_status(error)
            logger.error(
                "review_failed request_id=%s task_id=%s group=%s files=%d chars=%d error_code=%s provider_status=%s error_type=%s error=%s",
                request_id, request.task_id or "-", request.group_number or "-", len(request.files), total_chars,
                code, status or "-", type(error).__name__, safe_error_message(error),
            )
            raise HTTPException(
                status_code=503,
                detail="LLM provider is unavailable",
                headers={"X-CodeOps-Request-Id": request_id, "X-CodeOps-Error-Code": code},
            ) from error

    return app


app = create_app()
