from fastapi.testclient import TestClient

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
