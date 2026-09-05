package com.codeops.client.http;

import com.codeops.client.central.ProjectResolution;
import com.codeops.client.git.PrePushUpdate;
import com.codeops.client.git.RepositoryDiscovery;
import com.codeops.client.review.ReviewResult;
import com.codeops.client.review.ReviewWorkflow;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class GitClientOperations implements LocalClientOperations {
    private final RepositoryDiscovery repositories;
    private final ReviewWorkflow reviews;
    public GitClientOperations(RepositoryDiscovery repositories, ReviewWorkflow reviews) { this.repositories = repositories; this.reviews = reviews; }
    @Override public ProjectResolution resolve(Path repositoryPath, String remoteName) throws IOException, InterruptedException {
        return reviews.resolveProject(repositories.discover(repositoryPath, remoteName == null ? "origin" : remoteName));
    }
    @Override public ReviewResult reviewPrePush(Path repositoryPath, String remoteName, List<String> updates) throws IOException, InterruptedException {
        return reviews.reviewPrePush(repositories.discover(repositoryPath, remoteName == null ? "origin" : remoteName),
                updates.stream().map(PrePushUpdate::parse).toList());
    }
    @Override public ReviewResult reviewPreCommit(Path repositoryPath, String remoteName) throws IOException, InterruptedException {
        return reviews.reviewPreCommit(repositories.discover(repositoryPath, remoteName == null ? "origin" : remoteName));
    }
    @Override public ReviewResult reviewPostMerge(Path repositoryPath, String remoteName) throws IOException, InterruptedException {
        return reviews.reviewPostMerge(repositories.discover(repositoryPath, remoteName == null ? "origin" : remoteName));
    }
}
