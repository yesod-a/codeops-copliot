from pathlib import Path


MAX_READ_LINES = 500
MAX_SEARCH_RESULTS = 100


def _safe_path(repository: str, file_path: str) -> Path:
    root = Path(repository).expanduser().resolve()
    candidate = (root / file_path.replace("\\", "/")).resolve()
    if candidate != root and root not in candidate.parents:
        raise ValueError("file path must stay inside repository")
    return candidate


def read_repository_file(
    repository: str,
    file_path: str,
    start_line: int = 1,
    end_line: int | None = None,
    max_lines: int = MAX_READ_LINES,
) -> str:
    if start_line < 1:
        raise ValueError("start_line must be positive")
    path = _safe_path(repository, file_path)
    if not path.is_file():
        return ""

    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    effective_end = min(end_line or len(lines), start_line - 1 + min(max_lines, MAX_READ_LINES))
    return "\n".join(
        f"{number}|{lines[number - 1]}"
        for number in range(start_line, effective_end + 1)
        if number <= len(lines)
    )


def search_repository(repository: str, query: str, file_patterns: list[str] | None = None) -> list[dict]:
    if not query.strip():
        return []
    root = Path(repository).expanduser().resolve()
    patterns = file_patterns or ["*"]
    results: list[dict] = []
    for path in root.rglob("*"):
        if len(results) >= MAX_SEARCH_RESULTS:
            break
        if not path.is_file() or ".git" in path.parts:
            continue
        relative = path.relative_to(root).as_posix()
        if not any(path.match(pattern) or relative.endswith(pattern.lstrip("*")) for pattern in patterns):
            continue
        try:
            lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        except OSError:
            continue
        for line_number, content in enumerate(lines, 1):
            if query in content:
                results.append({"path": relative, "line": line_number, "content": content})
                if len(results) >= MAX_SEARCH_RESULTS:
                    break
    return results

