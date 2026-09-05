package com.codeops.client.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeops.client.central.CentralApiClient;
import com.codeops.client.central.CentralReviewRequest;
import com.codeops.client.central.CentralReviewResponse;
import com.codeops.client.central.ProjectResolution;
import com.codeops.client.credential.CredentialStore;
import com.codeops.client.git.ChangeCollector;
import com.codeops.client.git.LocalRepository;
import com.codeops.client.git.PrePushUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReviewWorkflowTest {
    private final LocalRepository repository = new LocalRepository(Path.of("C:/work/order-service"), "https://git.example.com/acme/order-service.git", "main", "abc123");
    private final PrePushUpdate update = new PrePushUpdate("refs/heads/main", "abc123", "refs/heads/main", "def456");

    @Test
    void skipsAnUnregisteredRepositoryWithoutSubmittingAReview() throws Exception {
        var central = new RecordingCentral(ProjectResolution.unregistered(), new CentralReviewResponse(false, null, null));
        var workflow = new ReviewWorkflow(central, new TokenStore(), new ChangeCollector(new com.codeops.client.git.GitCommandRunner()));

        ReviewResult result = workflow.reviewPrePush(repository, List.of(update));

        assertThat(result.blocked()).isFalse();
        assertThat(result.message()).contains("not registered");
        assertThat(central.reviewRequests).isEmpty();
    }

    @Test
    void returnsBlockedWhenTheCentralReviewBlocksThePush() throws Exception {
        var central = new RecordingCentral(new ProjectResolution(3L, "Order", "git.example.com/acme/order-service", "REVIEWER", true, true, "HIGH"),
                new CentralReviewResponse(true, "Policy blocked", new ObjectMapper().readTree("{\"findings\":[]}")));
        var workflow = new ReviewWorkflow(central, new TokenStore(), new FixedChanges());

        ReviewResult result = workflow.reviewPrePush(repository, List.of(update));

        assertThat(result.blocked()).isTrue();
        assertThat(result.message()).isEqualTo("Policy blocked");
        assertThat(central.reviewRequests).hasSize(1);
        assertThat(central.reviewRequests.getFirst().repositoryKey()).isEqualTo("git.example.com/acme/order-service");
    }

    private static final class TokenStore implements CredentialStore {
        @Override public void saveToken(String token) { }
        @Override public Optional<String> loadToken() { return Optional.of("cop_token"); }
        @Override public boolean isAvailable() { return true; }
    }
    private static final class FixedChanges extends ChangeCollector {
        FixedChanges() { super(new com.codeops.client.git.GitCommandRunner()); }
        @Override public List<com.codeops.client.git.ChangedFile> collectPrePush(LocalRepository repository, PrePushUpdate update) {
            return List.of(new com.codeops.client.git.ChangedFile("src/App.java", "M", 1, 0, "+class App {}"));
        }
    }
    private static final class RecordingCentral implements CentralApiClient {
        private final ProjectResolution resolution; private final CentralReviewResponse response;
        private final java.util.ArrayList<CentralReviewRequest> reviewRequests = new java.util.ArrayList<>();
        RecordingCentral(ProjectResolution resolution, CentralReviewResponse response) { this.resolution = resolution; this.response = response; }
        @Override public ProjectResolution resolveProject(String token, String remoteUrl) { return resolution; }
        @Override public CentralReviewResponse submitReview(String token, CentralReviewRequest request) { reviewRequests.add(request); return response; }
    }
}
