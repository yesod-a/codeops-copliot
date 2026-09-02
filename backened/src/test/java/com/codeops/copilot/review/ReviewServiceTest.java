package com.codeops.copilot.review;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewServiceTest {

    private final ReviewService reviewService = new ReviewService(
            new InMemoryReviewTaskRepository(),
            List.of(new SensitiveDataRule(), new TodoCommentRule()),
            new SimulatedAiReviewer()
    );

    @Test
    void submitsPendingTaskAndCompletesReviewWithStructuredFindings() {
        ReviewTask submitted = reviewService.submit(new ReviewRequest(
                "acme/order-service",
                42L,
                "Add payment endpoint",
                List.of(new ChangedFile("PaymentController.java", "String token = request.getHeader(\"Authorization\");"))
        ));

        assertThat(submitted.status()).isEqualTo(ReviewStatus.PENDING);

        ReviewTask completed = reviewService.process(submitted.id());

        assertThat(completed.status()).isEqualTo(ReviewStatus.COMPLETED);
        assertThat(completed.findings()).extracting(ReviewFinding::category)
                .contains("SECURITY", "MAINTAINABILITY");
        assertThat(completed.findings()).allSatisfy(finding -> {
            assertThat(finding.file()).isNotBlank();
            assertThat(finding.severity()).isNotNull();
            assertThat(finding.suggestion()).isNotBlank();
        });
    }

    @Test
    void rejectsDuplicateReviewForSameRepositoryAndPullRequest() {
        ReviewRequest request = new ReviewRequest(
                "acme/order-service", 42L, "Same PR", List.of()
        );

        ReviewTask first = reviewService.submit(request);
        ReviewTask second = reviewService.submit(request);

        assertThat(second.id()).isEqualTo(first.id());
    }
}
