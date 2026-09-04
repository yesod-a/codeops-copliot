"""Small, read-only repository tools exposed to the review orchestration layer."""

from pathlib import Path

from langchain_core.tools import StructuredTool

from .context import read_repository_file, search_repository


def file_read(repository: str, path: str, start_line: int = 1, end_line: int | None = None) -> str:
    """Read a bounded, line-numbered slice of a repository file."""
    return read_repository_file(repository, path, start_line=start_line, end_line=end_line)


def file_find(repository: str, pattern: str = "*") -> list[str]:
    """Find text files matching a glob without escaping the repository root."""
    root = Path(repository).expanduser().resolve()
    matches: list[str] = []
    for path in root.rglob(pattern):
        if not path.is_file() or ".git" in path.parts:
            continue
        matches.append(path.relative_to(root).as_posix())
    return sorted(matches)[:100]


def code_search(repository: str, query: str, file_patterns: list[str] | None = None) -> list[dict]:
    """Search repository text with a hard result limit."""
    return search_repository(repository, query, file_patterns)


REVIEW_TOOLS = (file_read, file_find, code_search)


def build_review_tools(repository: str) -> list[StructuredTool]:
    """Create repository-scoped LangChain tools with no path escape surface."""
    return [
        StructuredTool.from_function(
            lambda path, start_line=1, end_line=None: file_read(repository, path, start_line, end_line),
            name="file_read",
            description="Read a bounded, line-numbered slice of a repository file.",
        ),
        StructuredTool.from_function(
            lambda pattern="*": file_find(repository, pattern),
            name="file_find",
            description="Find repository files matching a glob pattern.",
        ),
        StructuredTool.from_function(
            lambda query, file_patterns=None: code_search(repository, query, file_patterns),
            name="code_search",
            description="Search repository text and return bounded matching lines.",
        ),
    ]
