package com.codeops.copilot.review;

import java.util.List;

public record ReviewRequest(
        String repository,
        long pullRequestNumber,
        String title,
        List<ChangedFile> files
) {
    public ReviewRequest {
        if (repository == null || repository.isBlank()) {
            throw new IllegalArgumentException("repository must not be blank");
        }
        if (pullRequestNumber <= 0) {
            throw new IllegalArgumentException("pullRequestNumber must be positive");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        files = files == null ? List.of() : List.copyOf(files);
    }
}
