package com.codeops.copilot.review;

import java.util.ArrayList;
import java.util.List;

public class TodoCommentRule implements ReviewRule {
    @Override
    public List<ReviewFinding> inspect(ReviewRequest request) {
        List<ReviewFinding> findings = new ArrayList<>();
        for (ChangedFile file : request.files()) {
            String[] lines = file.content().split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                if (lines[index].contains("TODO") || lines[index].contains("FIXME")) {
                    findings.add(new ReviewFinding(
                            "MAINTAINABILITY", Severity.LOW, file.path(), index + 1,
                            "The change contains an unresolved TODO or FIXME marker.",
                            "Create a tracked issue or finish the implementation before merging.",
                            lines[index].trim(), 0.95
                    ));
                }
            }
        }
        return findings;
    }
}
