package com.codeops.copilot.review.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!worker")
public class ReviewTaskEventConsumer {
    private final ObjectMapper objectMapper;
    private final ReviewTaskEventStream stream;

    public ReviewTaskEventConsumer(ObjectMapper objectMapper, ReviewTaskEventStream stream) {
        this.objectMapper = objectMapper;
        this.stream = stream;
    }

    @RabbitListener(queues = ReviewQueueConfiguration.EVENT_QUEUE)
    public void consume(String payload) {
        try {
            stream.publish(objectMapper.readValue(payload, ReviewTaskEvent.class));
        } catch (Exception ignored) {
            // A malformed progress event must not stop the consumer.
        }
    }
}
