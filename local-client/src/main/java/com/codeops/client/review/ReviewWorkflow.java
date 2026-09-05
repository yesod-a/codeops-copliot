package com.codeops.client.review;

import com.codeops.client.central.CentralApiClient;
import com.codeops.client.central.CentralReviewRequest;
import com.codeops.client.central.CentralReviewResponse;
import com.codeops.client.central.ProjectResolution;
import com.codeops.client.credential.CredentialStore;
import com.codeops.client.git.ChangeCollector;
import com.codeops.client.git.ChangedFile;
import com.codeops.client.git.LocalRepository;
import com.codeops.client.git.PrePushUpdate;
import java.io.IOException;
import java.util.List;

public final class ReviewWorkflow {
    private final CentralApiClient central;
    private final CredentialStore credentials;
    private final ChangeCollector changes;

    public ReviewWorkflow(CentralApiClient central, CredentialStore credentials, ChangeCollector changes) {
        this.central = central;
        this.credentials = credentials;
        this.changes = changes;
    }

    public ProjectResolution resolveProject(LocalRepository repository) throws IOException, InterruptedException {
        return central.resolveProject(token(), repository.remoteUrl());
    }

    public ReviewResult reviewPrePush(LocalRepository repository, List<PrePushUpdate> updates) throws IOException, InterruptedException {
        String token = token();
        ProjectResolution resolution = central.resolveProject(token, repository.remoteUrl());
        if (!resolution.registered()) return unregistered();
        if (!resolution.reviewEnabled() || !resolution.prePushEnabled()) return disabled("Pre-push");
        boolean blocked = false;
        String message = "No reviewable changes were found.";
        var findings = new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
        for (PrePushUpdate update : updates) {
            if (update.isDeletion()) continue;
            List<ChangedFile> files = changes.collectPrePush(repository, update);
            if (files.isEmpty()) continue;
            CentralReviewResponse response = submit(token, resolution, "pre-push",
                    "pre-push review: " + update.localRef() + " -> " + update.remoteRef(),
                    branchName(update.localRef()), update.localSha(), update.remoteSha(), files);
            blocked |= response.blocked();
            message = response.blockReason() == null || response.blockReason().isBlank() ? "Central review passed." : response.blockReason();
            findings.addAll(response.findings());
        }
        return new ReviewResult(blocked, message, findings);
    }

    public ReviewResult reviewPreCommit(LocalRepository repository) throws IOException, InterruptedException {
        return reviewSingleRange(repository, "pre-commit", "Pre-commit", changes.collectPreCommit(repository), repository.headCommit(), null,
                "pre-commit review: " + repository.branch());
    }

    public ReviewResult reviewPostMerge(LocalRepository repository) throws IOException, InterruptedException {
        return reviewSingleRange(repository, "post-merge", "Post-merge", changes.collectPostMerge(repository), repository.headCommit(), "HEAD^",
                "post-merge review: " + repository.branch());
    }

    private ReviewResult reviewSingleRange(LocalRepository repository, String trigger, String label, List<ChangedFile> files,
                                           String headCommit, String baseRef, String title) throws IOException, InterruptedException {
        String token = token();
        ProjectResolution resolution = central.resolveProject(token, repository.remoteUrl());
        if (!resolution.registered()) return unregistered();
        if (!resolution.reviewEnabled()) return disabled(label);
        if (files.isEmpty()) return new ReviewResult(false, "No reviewable changes were found.", List.of());
        CentralReviewResponse response = submit(token, resolution, trigger, title, repository.branch(), headCommit, baseRef, files);
        String message = response.blockReason() == null || response.blockReason().isBlank() ? "Central review passed." : response.blockReason();
        return new ReviewResult(response.blocked(), message, response.findings());
    }

    private CentralReviewResponse submit(String token, ProjectResolution resolution, String trigger, String title,
                                         String branch, String headCommit, String baseRef, List<ChangedFile> files) throws IOException, InterruptedException {
        return central.submitReview(token, new CentralReviewRequest(
                resolution.projectId(), resolution.repositoryKey(), trigger, title, branch, headCommit, baseRef, files));
    }

    private String token() throws IOException {
        return credentials.loadToken().orElseThrow(() -> new IOException("CodeOps Agent Token is unavailable"));
    }

    private ReviewResult unregistered() { return new ReviewResult(false, "Repository is not registered with CodeOps.", List.of()); }
    private ReviewResult disabled(String trigger) { return new ReviewResult(false, trigger + " review is disabled for this repository.", List.of()); }
    private String branchName(String reference) { return reference.replaceFirst("^refs/heads/", ""); }
}
