package com.codeops.copilot.review.git;

import java.util.List;

public record RepositorySnapshot(String repositoryPath, String branch, String headCommit, List<GitChangedFile> files) {
    public RepositorySnapshot {
        files = List.copyOf(files);
    }
}
