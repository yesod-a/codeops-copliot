from fastapi.testclient import TestClient
import logging

from app.config import Settings
from app.main import create_app


class StubReviewer:
    def review(self, repository, title, files):
        return [{
            "category": "QUALITY",
            "severity": "LOW",
            "file": files[0].path,
            "line": 1,
            "message": "建议补充测试",
            "suggestion": "增加单元测试",
            "evidence": "class App {}",
            "confidence": 0.9,
        }]


class FailingReviewer:
    def review(self, repository, title, files):
        raise RuntimeError("provider unavailable")


def test_review_endpoint_returns_structured_findings():
    client = TestClient(create_app(Settings(ai_enabled=True, ai_model="test"), StubReviewer()))

    response = client.post("/api/ai/review", json={
        "repository": "D:/repo",
        "title": "检查变更",
        "files": [{"path": "src/App.java", "content": "class App {}"}],
    })

    assert response.status_code == 200
    assert response.json()["findings"][0]["file"] == "src/App.java"


def test_review_endpoint_hides_provider_error_details():
    client = TestClient(create_app(Settings(ai_enabled=True, ai_model="test"), FailingReviewer()))

    response = client.post("/api/ai/review", json={
        "repository": "D:/repo",
        "title": "检查变更",
        "files": [{"path": "src/App.java", "content": "class App {}"}],
    })

    assert response.status_code == 503
    assert response.json()["detail"] == "LLM provider is unavailable"


def test_review_endpoint_logs_safe_failure_context(caplog):
    client = TestClient(create_app(Settings(ai_enabled=True, ai_model="test"), FailingReviewer()))

    with caplog.at_level(logging.ERROR, logger="codeops.llm"):
        response = client.post("/api/ai/review", json={
            "repository": "D:/repo",
            "title": "检查",
            "task_id": "task-123",
            "group_number": 2,
            "files": [{"path": "src/App.java", "content": "class App {}"}],
        })

    assert response.status_code == 503
    assert response.headers["X-CodeOps-Error-Code"] == "PROVIDER_UNAVAILABLE"
    message = " ".join(record.getMessage() for record in caplog.records)
    assert "task_id=task-123" in message
    assert "group=2" in message
    assert "files=1" in message
    assert "provider unavailable" in message
