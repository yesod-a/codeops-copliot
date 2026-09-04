import json
from fnmatch import fnmatch
from pathlib import Path


DEFAULT_RULES = {
    ".java": "检查空值、异常处理、事务边界、并发安全、资源释放和敏感信息泄漏。",
    ".kt": "检查空值、异常处理、协程安全、资源释放和敏感信息泄漏。",
    ".py": "检查异常处理、输入校验、资源释放、安全边界和测试覆盖。",
    ".js": "检查输入校验、异步错误处理、安全边界和测试覆盖。",
    ".ts": "检查类型安全、异步错误处理、安全边界和测试覆盖。",
    ".vue": "检查状态管理、异步错误处理、XSS 风险和组件边界。",
}


def resolve_rule(repository: str, file_path: str) -> str:
    """Resolve the most specific project rule for a repository-relative path."""
    root = Path(repository).expanduser().resolve()
    rule_file = root / ".opencodereview" / "rule.json"
    if not rule_file.is_file():
        return DEFAULT_RULES.get(Path(file_path).suffix.lower(), "")

    try:
        payload = json.loads(rule_file.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError):
        return DEFAULT_RULES.get(Path(file_path).suffix.lower(), "")

    normalized = file_path.replace("\\", "/").lstrip("/")
    matches: list[tuple[int, str]] = []
    for entry in payload.get("rules", []):
        pattern = entry.get("path")
        rule = entry.get("rule")
        if not isinstance(pattern, str) or not isinstance(rule, str):
            continue
        if fnmatch(normalized, pattern) or Path(normalized).match(pattern):
            specificity = sum(1 for token in pattern.split("/") if token and "*" not in token and "?" not in token)
            matches.append((specificity, rule))

    return max(matches, key=lambda item: item[0])[1] if matches else DEFAULT_RULES.get(Path(file_path).suffix.lower(), "")
