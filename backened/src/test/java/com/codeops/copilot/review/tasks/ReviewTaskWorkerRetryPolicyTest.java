package com.codeops.copilot.review.tasks;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTaskWorkerRetryPolicyTest {
    @Test
    void retryBudgetBelongsToTheCurrentGroup() {
        ReviewTaskGroupEntity group = new ReviewTaskGroupEntity(2, List.of());

        group.start();
        assertThat(ReviewTaskWorker.shouldRetryGroup(group)).isTrue();
        group.retry("LLM_UNAVAILABLE", "temporary");
        group.queue();
        group.start();
        assertThat(ReviewTaskWorker.shouldRetryGroup(group)).isTrue();
        group.retry("LLM_UNAVAILABLE", "temporary");
        group.queue();
        group.start();
        assertThat(ReviewTaskWorker.shouldRetryGroup(group)).isFalse();
    }

    @Test
    void manualRetryStartsANewAttemptBudgetForAnUnfinishedGroup() {
        ReviewTaskGroupEntity group = new ReviewTaskGroupEntity(2, List.of());
        group.start();
        group.retry("LLM_UNAVAILABLE", "temporary");
        group.queue();
        group.start();
        group.retry("LLM_UNAVAILABLE", "temporary");
        group.queue();
        group.start();
        group.fail("LLM_UNAVAILABLE", "temporary");

        group.resetForManualRetry();

        assertThat(group.getAttemptCount()).isZero();
        assertThat(group.getStatus()).isEqualTo(ReviewTaskGroupStatus.QUEUED);
        assertThat(ReviewTaskWorker.shouldRetryGroup(group)).isTrue();
    }
}
