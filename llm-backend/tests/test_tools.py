from app.tools import build_review_tools, code_search, file_find, file_read


def test_review_tools_are_bounded_to_repository(tmp_path):
    (tmp_path / "src").mkdir()
    (tmp_path / "src" / "App.java").write_text("class App {}\n")

    assert "1|class App {}" in file_read(str(tmp_path), "src/App.java")
    assert file_find(str(tmp_path), "*.java") == ["src/App.java"]
    assert code_search(str(tmp_path), "class App")[0]["path"] == "src/App.java"


def test_review_tools_can_be_bound_as_langchain_tools(tmp_path):
    (tmp_path / "src").mkdir()
    (tmp_path / "src" / "App.java").write_text("class App {}\n")
    tools = build_review_tools(str(tmp_path))

    assert {tool.name for tool in tools} == {"file_read", "file_find", "code_search"}
    assert "class App" in next(tool for tool in tools if tool.name == "file_read").invoke({"path": "src/App.java"})
