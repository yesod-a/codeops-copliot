package com.codeops.copilot.review.tasks;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class ReviewQueueConfiguration {
    public static final String EXECUTE_QUEUE = "codeops.review.execute";
    public static final String EXCHANGE = "codeops.review";
    public static final String ROUTING_KEY = "review.execute";
    public static final String EVENT_QUEUE = "codeops.review.task-events";

    @Bean DirectExchange reviewExchange() { return new DirectExchange(EXCHANGE, true, false); }
    @Bean Queue reviewExecuteQueue() { return new Queue(EXECUTE_QUEUE, true); }
    @Bean Queue reviewTaskEventQueue() { return new Queue(EVENT_QUEUE, true); }
    @Bean Binding reviewExecuteBinding(@Qualifier("reviewExecuteQueue") Queue reviewExecuteQueue, DirectExchange reviewExchange) {
        return BindingBuilder.bind(reviewExecuteQueue).to(reviewExchange).with(ROUTING_KEY);
    }

    @Bean Binding reviewTaskEventBinding(@Qualifier("reviewTaskEventQueue") Queue reviewTaskEventQueue, DirectExchange reviewExchange) {
        return BindingBuilder.bind(reviewTaskEventQueue).to(reviewExchange).with(ReviewTaskEventPublisher.ROUTING_KEY);
    }
}
