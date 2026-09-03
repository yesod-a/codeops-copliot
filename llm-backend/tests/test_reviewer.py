import json

from app.config import Settings
from app.models import ReviewFile
from app.reviewer import AiReviewer


class FakeModel:
    def __init__(self, content: str):
        self.content = content
        self.messages = None

    def invoke(self, messages):
        self.messages = messages
        return type("Message", (), {"content": self.content})()


def test_reviewer_parses_structured_model_findings():
    model = FakeModel(json.dumps({
        "findings": [{
            "category": "SECURITY",
            "severity": "HIGH",
            "file": "src/App.java",
            "line": 4,
            "message": "发现硬编码密钥",
            "suggestion": "使用环境变量",
            "evidence": "apiKey = \"secret\"",
            "confidence": 0.97,
        }]
    }))
    reviewer = AiReviewer(Settings(ai_enabled=True, ai_api_key="test", ai_model="test"), model=model)

    findings = reviewer.review(
        "D:/repo",
        "检查安全问题",
        [ReviewFile(path="src/App.java", content='apiKey = "secret"')],
    )

    assert len(findings) == 1
    assert findings[0].file == "src/App.java"
    assert findings[0].severity == "HIGH"
    assert model.messages is not None


def test_reviewer_rejects_invalid_model_output():
    reviewer = AiReviewer(Settings(ai_enabled=True, ai_api_key="test", ai_model="test"), model=FakeModel("not json"))

    try:
        reviewer.review("D:/repo", "检查", [ReviewFile(path="src/App.java", content="class App {}")])
    except ValueError as error:
        assert "JSON" in str(error)
    else:
        raise AssertionError("invalid model output should fail")
