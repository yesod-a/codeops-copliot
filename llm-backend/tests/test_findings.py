from app.findings import normalize_findings
from app.models import ReviewFile


def test_normalize_findings_discards_unknown_files_and_invalid_lines():
    files = [ReviewFile(path="src/App.java", content="1|class App {}")]
    findings = [
        {
            "category": "BUG",
            "severity": "HIGH",
            "file": "src/App.java",
            "line": 1,
            "message": "问题",
            "suggestion": "修复",
            "evidence": "class App {}",
            "confidence": 0.9,
        },
        {
            "category": "BUG",
            "severity": "HIGH",
            "file": "src/Other.java",
            "line": 1,
            "message": "不应保留",
            "suggestion": "删除",
            "confidence": 0.9,
        },
        {
            "category": "BUG",
            "severity": "HIGH",
            "file": "src/App.java",
            "line": 0,
            "message": "无效",
            "suggestion": "删除",
            "confidence": 0.9,
        },
    ]

    result = normalize_findings(findings, files)

    assert len(result) == 1
    assert result[0].file == "src/App.java"


def test_normalize_findings_normalizes_severity_and_line_range():
    result = normalize_findings([{
        "category": "quality",
        "severity": "high",
        "file": "src/App.java",
        "line": 4,
        "start_line": 4,
        "end_line": 6,
        "message": "问题",
        "suggestion": "修复",
        "evidence": "return value;",
        "confidence": 0.8,
    }], [ReviewFile(path="src/App.java", content="class App {}")])

    assert result[0].severity == "HIGH"
    assert result[0].start_line == 4
    assert result[0].end_line == 6


def test_normalize_findings_rejects_invalid_line_ranges():
    result = normalize_findings([{
        "category": "quality",
        "severity": "HIGH",
        "file": "src/App.java",
        "line": 4,
        "start_line": 7,
        "end_line": 6,
        "message": "问题",
        "suggestion": "修复",
        "evidence": "return value;",
        "confidence": 0.8,
    }], [ReviewFile(path="src/App.java", content="class App {}")])

    assert result == []


def test_normalize_findings_rebuilds_evidence_from_unified_diff():
    patch = """diff --git a/src/App.java b/src/App.java
--- a/src/App.java
+++ b/src/App.java
@@ -1,3 +1,4 @@
 class App {
+    return user.getName();
 }
"""

    result = normalize_findings([{
        "category": "BUG",
        "severity": "HIGH",
        "file": "src/App.java",
        "line": 2,
        "message": "可能为空指针",
        "suggestion": "增加判空",
        "evidence": "模型生成的解释，不是代码",
        "confidence": 0.9,
    }], [ReviewFile(path="src/App.java", content=patch)])

    assert len(result) == 1
    assert result[0].evidence == "    return user.getName();"


def test_normalize_findings_does_not_keep_evidence_not_present_in_source():
    result = normalize_findings([{
        "category": "BUG",
        "severity": "HIGH",
        "file": "src/App.java",
        "line": 2,
        "message": "问题",
        "suggestion": "修复",
        "evidence": "这是一段模型解释",
        "confidence": 0.9,
    }], [ReviewFile(path="src/App.java", content="class App {}\n")])

    assert len(result) == 1
    assert result[0].evidence == ""


def test_normalize_findings_replaces_oversized_model_evidence_before_validation():
    patch = """@@ -10,1 +10,1 @@
-old value
+actual = value;
"""
    result = normalize_findings([{
        "category": "BUG",
        "severity": "HIGH",
        "file": "src/App.java",
        "line": 10,
        "message": "问题",
        "suggestion": "修复",
        "evidence": "x" * 10_000,
        "confidence": 0.9,
    }], [ReviewFile(path="src/App.java", content=patch)])

    assert len(result) == 1
    assert result[0].evidence == "actual = value;"
