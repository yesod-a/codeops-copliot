from collections.abc import Iterable

from .models import ReviewFile, ReviewFinding


def normalize_findings(raw_findings: Iterable[dict], files: list[ReviewFile]) -> list[ReviewFinding]:
    allowed_paths = {file.path.replace("\\", "/") for file in files}
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
