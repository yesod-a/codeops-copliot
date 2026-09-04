import json

from app.config import Settings
from app.models import ReviewFile
from app.reviewer import AiReviewer, build_review_plan, group_review_files


class FakeModel:
    def __init__(self, content: str):
        self.content = content
        self.messages = None
        self.calls = 0
        self.all_messages = []

    def invoke(self, messages):
        self.calls += 1
        self.messages = messages
        self.all_messages.append(messages)
        return type("Message", (), {"content": self.content})()


class ToolCallingModel(FakeModel):
    def __init__(self, content: str):
        super().__init__(content)
        self.bound = False

    def bind_tools(self, tools):
        self.bound = {tool.name for tool in tools}
        return self

    def invoke(self, messages):
        self.calls += 1
        self.all_messages.append(messages)
        if self.calls == 1:
            return type("Message", (), {
                "content": "",
                "tool_calls": [{"name": "file_read", "args": {"path": "src/App.java"}, "id": "call-1"}],
            })()
        return type("Message", (), {"content": self.content, "tool_calls": []})()


class PlanThenReviewModel(FakeModel):
    def invoke(self, messages):
        self.calls += 1
        self.all_messages.append(messages)
        content = "检查跨文件事务边界" if self.calls == 1 else self.content
        return type("Message", (), {"content": content, "tool_calls": []})()


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


def test_reviewer_reviews_each_file_with_project_context(tmp_path):
    (tmp_path / ".opencodereview").mkdir()
    (tmp_path / ".opencodereview" / "rule.json").write_text(
        '{"rules":[{"path":"**/*.java","rule":"Check transaction boundaries"}]}'
    )
    (tmp_path / "src").mkdir()
    (tmp_path / "src" / "App.java").write_text("class App { int value; }\n")
    model = FakeModel(json.dumps({"findings": []}))
    reviewer = AiReviewer(Settings(ai_enabled=True, ai_api_key="test", ai_model="test"), model=model)

    findings = reviewer.review(
        str(tmp_path),
        "检查变更",
        [
            ReviewFile(path="src/App.java", content="diff --git a/src/App.java b/src/App.java"),
            ReviewFile(path="src/Other.java", content="diff --git a/src/Other.java b/src/Other.java"),
        ],
    )

    assert findings == []
    assert model.calls == 1
    rendered = "\n".join(
        getattr(message, "content", str(message))
        for messages in model.all_messages
        for message in messages
    )
    assert "Check transaction boundaries" in rendered
    assert "class App" in rendered


def test_reviewer_adds_plan_context_for_large_changes(tmp_path):
    (tmp_path / "src").mkdir()
    (tmp_path / "src" / "App.java").write_text("class App {\n" + "  int value;\n" * 700 + "}\n")
    model = FakeModel(json.dumps({"findings": []}))
    reviewer = AiReviewer(Settings(ai_enabled=True, ai_api_key="test", ai_model="test"), model=model)

    reviewer.review(str(tmp_path), "大型变更", [
        ReviewFile(path="src/App.java", content="+" + "x" * 13000)
    ])

    rendered = "\n".join(
        getattr(message, "content", str(message))
        for messages in model.all_messages
        for message in messages
    )
    assert "评审计划" in rendered
    assert "大型变更" in rendered


def test_reviewer_runs_bound_repository_tool_calls(tmp_path):
    (tmp_path / "src").mkdir()
    (tmp_path / "src" / "App.java").write_text("class App {}\n")
    model = ToolCallingModel(json.dumps({"findings": []}))
    reviewer = AiReviewer(Settings(ai_enabled=True, ai_api_key="test", ai_model="test"), model=model)

    assert reviewer.review(str(tmp_path), "工具调用", [ReviewFile(path="src/App.java", content="diff")]) == []
    assert model.bound == {"file_read", "file_find", "code_search"}
    assert model.calls == 2


def test_reviewer_runs_plan_before_main_review_for_large_changes(tmp_path):
    (tmp_path / "src").mkdir()
    (tmp_path / "src" / "App.java").write_text("class App {}\n")
    model = PlanThenReviewModel(json.dumps({"findings": []}))
    reviewer = AiReviewer(Settings(ai_enabled=True, ai_api_key="test", ai_model="test"), model=model)

    reviewer.review(str(tmp_path), "大型评审", [ReviewFile(path="src/App.java", content="+" + "x" * 13000)])

    assert model.calls == 2
    rendered_main = "\n".join(getattr(message, "content", str(message)) for message in model.all_messages[1])
    assert "检查跨文件事务边界" in rendered_main


def test_group_review_files_uses_file_and_character_limits():
    files = [
        ReviewFile(path="a.java", content="a" * 20000),
        ReviewFile(path="b.java", content="b" * 20000),
        ReviewFile(path="c.java", content="c" * 35000),
        ReviewFile(path="d.java", content="d" * 10000),
    ]

    groups = group_review_files(files)

    assert [[file.path for file in group] for group in groups] == [
        ["a.java", "b.java"], ["c.java"], ["d.java"]
    ]


def test_small_group_plan_is_based_on_size_not_file_count():
    files = [ReviewFile(path=f"{index}.java", content="diff") for index in range(8)]

    assert build_review_plan(files).startswith("小变更")
