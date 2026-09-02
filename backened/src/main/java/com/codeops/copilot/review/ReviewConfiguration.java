package com.codeops.copilot.review;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ReviewConfiguration {
    @Bean
    ReviewTaskRepository reviewTaskRepository() {
        return new InMemoryReviewTaskRepository();
    }

    @Bean
    AiReviewer aiReviewer() {
        return new SimulatedAiReviewer();
    }

    @Bean
    ReviewService reviewService(ReviewTaskRepository repository, AiReviewer aiReviewer) {
        return new ReviewService(repository, List.of(new SensitiveDataRule(), new TodoCommentRule()), aiReviewer);
    }
}
