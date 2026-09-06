package com.codeops.copilot.review.tasks;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReviewQueueConfiguration {
    public static final String EXECUTE_QUEUE = "codeops.review.execute";
    public static final String EXCHANGE = "codeops.review";
    public static final String ROUTING_KEY = "review.execute";

    @Bean DirectExchange reviewExchange() { return new DirectExchange(EXCHANGE, true, false); }
    @Bean Queue reviewExecuteQueue() { return new Queue(EXECUTE_QUEUE, true); }
    @Bean Binding reviewExecuteBinding(Queue reviewExecuteQueue, DirectExchange reviewExchange) {
        return BindingBuilder.bind(reviewExecuteQueue).to(reviewExchange).with(ROUTING_KEY);
    }
}
