from collections.abc import Iterable
import re

from .models import ReviewFile, ReviewFinding


_HUNK_HEADER = re.compile(r"^@@ -\d+(?:,\d+)? \+(?P<new_start>\d+)(?:,\d+)? @@", re.MULTILINE)
_MAX_EVIDENCE_CHARS = 4_000


def _diff_source_lines(content: str) -> dict[int, str]:
    """Return source text keyed by new-file line number from a unified diff."""
    lines: dict[int, str] = {}
    new_line: int | None = None
    for raw_line in content.splitlines():
        header = _HUNK_HEADER.match(raw_line)
        if header:
            new_line = int(header.group("new_start"))
            continue
        if new_line is None or raw_line.startswith(("--- ", "+++ ", "\\")):
            continue
        marker = raw_line[:1]
        if marker in {"+", " "}:
            lines[new_line] = raw_line[1:]
            new_line += 1
        elif marker == "-":
            # Removed lines belong to the old file and must not be used as evidence.
            continue
    return lines


def _source_lines(content: str) -> dict[int, str]:
    if _HUNK_HEADER.search(content):
        return _diff_source_lines(content)
    return {number: line for number, line in enumerate(content.splitlines(), 1)}


def extract_evidence(content: str, start_line: int, end_line: int | None = None) -> str:
    """Extract exact source text for a finding's new-file line range."""
    if not content or start_line < 1:
        return ""
    effective_end = max(start_line, end_line or start_line)
    selected = _source_lines(content)
    evidence = [selected[number] for number in range(start_line, effective_end + 1) if number in selected]
    return "\n".join(evidence)[:_MAX_EVIDENCE_CHARS]


def normalize_findings(raw_findings: Iterable[dict], files: list[ReviewFile]) -> list[ReviewFinding]:
    allowed_paths = {file.path.replace("\\", "/") for file in files}
    file_by_path = {file.path.replace("\\", "/"): file for file in files}
    normalized: list[ReviewFinding] = []
    for raw in raw_findings:
        if isinstance(raw, ReviewFinding):
            candidate = raw.model_dump()
        elif isinstance(raw, dict):
            candidate = dict(raw)
        else:
            continue
        severity = candidate.get("severity")
        if isinstance(severity, str):
            aliases = {
                "CRITICAL": "CRITICAL", "BLOCKER": "CRITICAL",
                "HIGH": "HIGH", "ERROR": "HIGH",
                "MEDIUM": "MEDIUM", "WARN": "MEDIUM", "WARNING": "MEDIUM",
                "LOW": "LOW", "INFO": "LOW",
            }
            candidate["severity"] = aliases.get(severity.strip().upper(), severity.strip().upper())
        if candidate.get("start_line") is None and candidate.get("line") is not None:
            candidate["start_line"] = candidate["line"]
        if candidate.get("end_line") is None and candidate.get("start_line") is not None:
            candidate["end_line"] = candidate["start_line"]
        source_file = file_by_path.get(str(candidate.get("file", "")).replace("\\", "/"))
        if source_file is not None:
            # Evidence is always rebuilt from submitted source/diff text before
            # validation, so oversized or prose-only model evidence is harmless.
            raw_start = candidate.get("start_line") or candidate.get("line")
            raw_end = candidate.get("end_line")
            candidate["evidence"] = extract_evidence(
                source_file.content,
                raw_start if isinstance(raw_start, int) else -1,
                raw_end if isinstance(raw_end, int) else None,
            )
        try:
            finding = ReviewFinding.model_validate(candidate)
        except Exception:
            continue
        if finding.file.replace("\\", "/") not in allowed_paths or finding.line < 1:
            continue
        if finding.start_line and finding.end_line and finding.end_line < finding.start_line:
            continue
        normalized.append(finding)
        if len(normalized) >= 100:
            break
    return normalized
