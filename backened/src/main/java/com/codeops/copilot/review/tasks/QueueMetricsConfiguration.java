package com.codeops.copilot.review.tasks;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Registers low-cardinality queue gauges. The publisher updates the values after
 * each poll; a zero value is preferable to querying RabbitMQ on every scrape.
 */
@Configuration
public class QueueMetricsConfiguration {
    private final AtomicInteger queueDepth = new AtomicInteger();
    private final AtomicInteger pendingOutbox = new AtomicInteger();

    public QueueMetricsConfiguration(MeterRegistry registry) {
        Gauge.builder("codeops_review_queue_depth", queueDepth, AtomicInteger::get)
                .tag("queue", ReviewQueueConfiguration.EXECUTE_QUEUE).register(registry);
        Gauge.builder("codeops_outbox_pending_events", pendingOutbox, AtomicInteger::get)
                .register(registry);
    }

    public void updateQueueDepth(int value) { queueDepth.set(Math.max(0, value)); }
    public void updatePendingOutbox(int value) { pendingOutbox.set(Math.max(0, value)); }
}
