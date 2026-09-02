package com.codeops.copilot.review;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SensitiveDataRule implements ReviewRule {
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|secret|api[_-]?key|authorization)"
    );

    @Override
    public List<ReviewFinding> inspect(ReviewRequest request) {
        List<ReviewFinding> findings = new ArrayList<>();
        for (ChangedFile file : request.files()) {
            String[] lines = file.content().split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                if (SECRET.matcher(lines[index]).find()) {
                    findings.add(new ReviewFinding(
                            "SECURITY", Severity.HIGH, file.path(), index + 1,
                            "Sensitive data may be read or assigned directly in application code.",
                            "Move secrets to a managed secret store and avoid logging or copying credentials.",
                            lines[index].trim(), 0.98
                    ));
                }
            }
        }
        return findings;
    }
}
