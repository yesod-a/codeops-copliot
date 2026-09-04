from pathlib import Path

from app.rules import resolve_rule


def test_resolve_rule_prefers_project_pattern_for_a_file(tmp_path: Path):
    (tmp_path / ".opencodereview").mkdir()
    (tmp_path / ".opencodereview" / "rule.json").write_text(
        '{"rules":[{"path":"**/*.java","rule":"Check transactions"}]}'
    )

    assert resolve_rule(str(tmp_path), "src/PaymentService.java") == "Check transactions"


def test_resolve_rule_returns_empty_for_unmatched_file(tmp_path: Path):
    assert resolve_rule(str(tmp_path), "src/README.txt") == ""

