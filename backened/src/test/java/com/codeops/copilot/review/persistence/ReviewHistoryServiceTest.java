package com.codeops.copilot.review.persistence;

import com.codeops.copilot.review.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(ReviewHistoryService.class)
class ReviewHistoryServiceTest {

    @Autowired
    private ReviewHistoryService service;

    @Test
    void savesReviewWithFilesAndFindingsAndReloadsIt() {
        UUID requestId = UUID.randomUUID();
        ReviewHistoryService.ReviewHistoryView saved = service.save(new ReviewHistoryService.SaveReviewCommand(
                requestId, "C:/repo", "repo", "Review changes", "GIT", "WORKTREE", null,
                "main", "a".repeat(40), "gpt-4o-mini",
                List.of(new ReviewHistoryService.FileCommand(
                        "src/App.java", "MODIFIED", 3, 1, "@@", "hash")),
                List.of(new ReviewHistoryService.FindingCommand(
                        "src/App.java", "SECURITY", Severity.HIGH, 2, "问题", "建议", "证据", 0.95))
        ));

        ReviewHistoryService.ReviewHistoryView loaded = service.get(saved.id());

        assertThat(loaded.id()).isEqualTo(saved.id());
        assertThat(loaded.repository()).isEqualTo("C:/repo");
        assertThat(loaded.files()).hasSize(1);
        assertThat(loaded.findings()).hasSize(1);
        assertThat(loaded.riskScore()).isEqualTo(85);
    }

    @Test
    void savesTheSameRequestOnlyOnceAndDeletesCascadedData() {
        UUID requestId = UUID.randomUUID();
        ReviewHistoryService.SaveReviewCommand command = new ReviewHistoryService.SaveReviewCommand(
                requestId, "C:/repo", "repo", "Review changes", "GIT", "WORKTREE", null,
                "main", "a".repeat(40), "gpt-4o-mini", List.of(), List.of());

        ReviewHistoryService.ReviewHistoryView first = service.save(command);
        ReviewHistoryService.ReviewHistoryView second = service.save(command);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(service.list(20, 0)).hasSize(1);

        service.delete(first.id());

        assertThat(service.list(20, 0)).isEmpty();
    }
}
