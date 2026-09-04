from pathlib import Path

import pytest

from app.context import read_repository_file, search_repository


def test_read_repository_file_returns_numbered_bounded_lines(tmp_path: Path):
    (tmp_path / "src").mkdir()
    (tmp_path / "src" / "App.java").write_text("one\ntwo\nthree\n")

    result = read_repository_file(str(tmp_path), "src/App.java", start_line=2, end_line=3)

    assert result == "2|two\n3|three"


def test_read_repository_file_rejects_path_traversal(tmp_path: Path):
    with pytest.raises(ValueError, match="inside repository"):
        read_repository_file(str(tmp_path), "../secret.txt")


def test_search_repository_returns_bounded_matches(tmp_path: Path):
    (tmp_path / "src").mkdir()
    (tmp_path / "src" / "App.java").write_text("token = 1\n")

    result = search_repository(str(tmp_path), "token")

    assert result == [{"path": "src/App.java", "line": 1, "content": "token = 1"}]

