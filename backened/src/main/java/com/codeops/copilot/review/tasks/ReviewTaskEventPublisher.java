package com.codeops.copilot.review.tasks;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Publishes task progress across the worker and web containers. */
@Service
public class ReviewTaskEventPublisher {
    public static final String EXCHANGE = ReviewQueueConfiguration.EXCHANGE;
    public static final String ROUTING_KEY = "review.task-event";
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public ReviewTaskEventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishAfterCommit(ReviewTaskEvent event) {
        if (event == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish(event); }
            });
        } else {
            publish(event);
        }
    }

    private void publish(ReviewTaskEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException | RuntimeException ignored) {
            // SSE is an optimization; the task API remains the source of truth.
        }
    }
}
