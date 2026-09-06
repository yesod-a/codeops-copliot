package com.codeops.copilot.review.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTaskWorkerTransactionTest {
    @Test
    void rabbitMessageHandlingRunsInsideATransactionForLazyTaskRelations() throws Exception {
        assertThat(ReviewTaskWorker.class.getMethod("consume", String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }
}
