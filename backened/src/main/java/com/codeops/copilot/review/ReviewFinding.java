package com.codeops.copilot.review;

public record ReviewFinding(
        String category,
        Severity severity,
        String file,
        int line,
        String message,
        String suggestion,
        String evidence,
        double confidence
) {
}
