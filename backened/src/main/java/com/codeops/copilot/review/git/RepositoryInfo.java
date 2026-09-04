package com.codeops.copilot.review.git;

import java.nio.file.Path;

public record RepositoryInfo(Path repositoryPath, String branch, String headCommit) {
}
