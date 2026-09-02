package com.codeops.copilot.review;

import org.springframework.scheduling.annotation.Async;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReviewService {
    private final ReviewTaskRepository repository;
    private final List<ReviewRule> rules;
    private final AiReviewer aiReviewer;

    public ReviewService(ReviewTaskRepository repository, List<ReviewRule> rules, AiReviewer aiReviewer) {
        this.repository = repository;
        this.rules = List.copyOf(rules);
        this.aiReviewer = aiReviewer;
    }

    public ReviewTask submit(ReviewRequest request) {
        return repository.findByPullRequest(request.repository(), request.pullRequestNumber())
                .orElseGet(() -> repository.save(ReviewTask.pending(request)));
    }

    @Async
    public void processAsync(UUID taskId) {
        process(taskId);
    }

    public ReviewTask process(UUID taskId) {
        ReviewTask task = get(taskId);
        if (task.status() == ReviewStatus.COMPLETED) {
            return task;
        }

        ReviewTask processing = repository.save(task.withStatus(ReviewStatus.PROCESSING, List.of(), null));
        try {
            ReviewRequest request = new ReviewRequest(
                    processing.repository(), processing.pullRequestNumber(), processing.title(), processing.files()
            );
            List<ReviewFinding> findings = new ArrayList<>();
            for (ReviewRule rule : rules) {
                findings.addAll(rule.inspect(request));
            }
            findings.addAll(aiReviewer.review(request));
            return repository.save(processing.withStatus(ReviewStatus.COMPLETED, findings, null));
        } catch (RuntimeException exception) {
            return repository.save(processing.withStatus(ReviewStatus.FAILED, List.of(), exception.getMessage()));
        }
    }

    public ReviewTask get(UUID taskId) {
        return repository.findById(taskId)
                .orElseThrow(() -> new ReviewNotFoundException(taskId));
    }

    public static class ReviewNotFoundException extends RuntimeException {
        public ReviewNotFoundException(UUID taskId) {
            super("Review task not found: " + taskId);
        }
    }
}
