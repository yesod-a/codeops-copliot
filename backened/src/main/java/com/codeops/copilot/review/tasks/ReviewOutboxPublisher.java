package com.codeops.copilot.review.tasks;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!worker")
public class ReviewOutboxPublisher {
    private final ReviewOutboxEventRepository events;
    private final RabbitTemplate rabbitTemplate;

    public ReviewOutboxPublisher(ReviewOutboxEventRepository events, RabbitTemplate rabbitTemplate) {
        this.events = events;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${codeops.review-queue.outbox-delay-ms:1000}")
    @Transactional
    public void publishPending() {
        events.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, 50)).forEach(event -> {
            try {
                rabbitTemplate.convertAndSend(ReviewQueueConfiguration.EXCHANGE, ReviewQueueConfiguration.ROUTING_KEY,
                        event.getPayload());
                event.markPublished();
            } catch (RuntimeException exception) {
                event.markFailedAttempt();
            }
        });
    }
}
