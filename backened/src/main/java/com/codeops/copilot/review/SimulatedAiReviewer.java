package com.codeops.copilot.review;

import java.util.ArrayList;
import java.util.List;

public class SimulatedAiReviewer implements AiReviewer {
    @Override
    public List<ReviewFinding> review(ReviewRequest request) {
        List<ReviewFinding> findings = new ArrayList<>();
        for (ChangedFile file : request.files()) {
            if (file.path().endsWith("Controller.java") && file.content().contains("Repository")) {
                findings.add(new ReviewFinding(
                        "ARCHITECTURE", Severity.MEDIUM, file.path(), 1,
                        "The controller appears to depend directly on a repository.",
                        "Move data access behind an application service to keep the HTTP layer focused on transport concerns.",
                        "Controller references Repository", 0.84
                ));
            }
            if (file.path().endsWith("Controller.java") && file.content().contains("request.getHeader")) {
                findings.add(new ReviewFinding(
                        "MAINTAINABILITY", Severity.MEDIUM, file.path(), 1,
                        "The controller reads infrastructure headers directly instead of using a dedicated authentication abstraction.",
                        "Resolve the authenticated principal through Spring Security and keep request parsing separate from business logic.",
                        "Controller reads request header", 0.79
                ));
            }
        }
        return findings;
    }
}
