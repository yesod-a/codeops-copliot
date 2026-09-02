package com.codeops.copilot.review;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewTask(
        UUID id,
        String repository,
        long pullRequestNumber,
        String title,
        List<ChangedFile> files,
        ReviewStatus status,
        List<ReviewFinding> findings,
        Instant createdAt,
        Instant updatedAt,
        String error
) {
    public ReviewTask {
        files = files == null ? List.of() : List.copyOf(files);
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    public static ReviewTask pending(ReviewRequest request) {
        Instant now = Instant.now();
        return new ReviewTask(
                UUID.randomUUID(), request.repository(), request.pullRequestNumber(), request.title(), request.files(),
                ReviewStatus.PENDING, List.of(), now, now, null
        );
    }

    public ReviewTask withStatus(ReviewStatus nextStatus, List<ReviewFinding> nextFindings, String nextError) {
        return new ReviewTask(id, repository, pullRequestNumber, title, files, nextStatus,
                nextFindings, createdAt, Instant.now(), nextError);
    }
}
