package com.codeops.copilot.review.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTaskRuntimeProfileTest {
    @Test
    void runsTheOutboxPublisherOnlyInTheWebBackend() {
        assertThat(profileValues(ReviewOutboxPublisher.class)).containsExactly("!worker");
    }

    @Test
    void runsTheQueueConsumerOnlyInTheReviewWorker() {
        assertThat(profileValues(ReviewTaskWorker.class)).containsExactly("worker");
    }

    private String[] profileValues(Class<?> type) {
        Profile profile = type.getAnnotation(Profile.class);
        return profile == null ? new String[0] : Arrays.stream(profile.value()).sorted().toArray(String[]::new);
    }
}
