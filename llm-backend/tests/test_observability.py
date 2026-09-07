import json

from app.config import Settings
from app.models import ReviewFile
from app.reviewer import AiReviewer


class UsageModel:
    def __init__(self, content, usage=None):
        self.content = content
        self.usage = usage
        self.calls = 0

    def invoke(self, messages):
        self.calls += 1
        return type("Message", (), {
            "content": self.content,
            "tool_calls": [],
            "usage_metadata": self.usage,
        })()


class ToolFailureModel(UsageModel):
    def bind_tools(self, tools):
        return self

    def invoke(self, messages):
        self.calls += 1
        if self.calls == 1:
            return type("Message", (), {
                "content": "",
                "tool_calls": [{"name": "missing_tool", "args": {}, "id": "call-1"}],
                "usage_metadata": {"input_tokens": 2, "output_tokens": 1, "total_tokens": 3},
            })()
        return type("Message", (), {
            "content": json.dumps({"findings": []}),
            "tool_calls": [],
            "usage_metadata": {"input_tokens": 4, "output_tokens": 2, "total_tokens": 6},
        })()


def test_review_with_metrics_extracts_usage_and_cost():
    model = UsageModel(
        json.dumps({"findings": []}),
        {"input_tokens": 100, "output_tokens": 25, "total_tokens": 125},
    )
    settings = Settings(
        ai_enabled=True,
        ai_api_key="test",
        ai_model="test",
        ai_model_pricing={"test": {"input": 1.0, "output": 2.0}},
    )
    run = AiReviewer(settings, model=model).review_with_metrics(
        "D:/repo", "检查", [ReviewFile(path="src/App.java", content="class App {}")]
    )

    assert run.findings == []
    assert run.metrics.input_tokens == 100
    assert run.metrics.output_tokens == 25
    assert run.metrics.total_tokens == 125
    assert run.metrics.estimated_cost == 0.15
    assert run.metrics.duration_ms >= 0
    assert run.metrics.phase_durations_ms["REVIEW"] >= 0


def test_review_with_metrics_records_tool_failure_without_failing_review():
    run = AiReviewer(
        Settings(ai_enabled=True, ai_api_key="test", ai_model="test"),
        model=ToolFailureModel(json.dumps({"findings": []})),
    ).review_with_metrics(
        "D:/repo", "工具", [ReviewFile(path="src/App.java", content="diff")]
    )

    assert run.findings == []
    assert len(run.metrics.tool_calls) == 1
    assert run.metrics.tool_calls[0].name == "missing_tool"
    assert run.metrics.tool_calls[0].status == "FAILED"
    assert run.metrics.tool_calls[0].error_code == "UNKNOWN_TOOL"


def test_review_compatibility_still_returns_findings_only():
    model = UsageModel(json.dumps({"findings": []}))
    reviewer = AiReviewer(Settings(ai_enabled=True, ai_api_key="test"), model=model)
    result = reviewer.review("D:/repo", "检查", [ReviewFile(path="a.java", content="diff")])
    assert result == []
