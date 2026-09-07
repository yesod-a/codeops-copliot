package com.codeops.copilot.review.tasks;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTaskEventStreamTest {
    @Test
    void subscribesAndPublishesNamedTaskEvents() throws Exception {
        ReviewTaskEventStream stream = new ReviewTaskEventStream();

        SseEmitter emitter = stream.subscribe("task-1");
        AtomicReference<SseEmitter.SseEventBuilder> received = new AtomicReference<>();
        stream.setSenderForTest((target, event) -> {
            if (target == emitter) received.set(event);
        });

        stream.publish(new ReviewTaskEvent("task-1", "GROUP_COMPLETED", "RUNNING", 2, 1, 3));

        assertThat(stream.subscriberCount("task-1")).isEqualTo(1);
        assertThat(received.get()).isNotNull();
    }

    @Test
    void removesEmitterWhenConnectionCloses() {
        ReviewTaskEventStream stream = new ReviewTaskEventStream();
        SseEmitter emitter = stream.subscribe("task-1");

        stream.remove("task-1", emitter);

        assertThat(stream.subscriberCount("task-1")).isZero();
    }
}
