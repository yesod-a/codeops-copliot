package com.codeops.copilot.review;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryReviewTaskRepository implements ReviewTaskRepository {
    private final ConcurrentMap<UUID, ReviewTask> tasks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> pullRequests = new ConcurrentHashMap<>();

    @Override
    public ReviewTask save(ReviewTask task) {
        tasks.put(task.id(), task);
        pullRequests.putIfAbsent(key(task.repository(), task.pullRequestNumber()), task.id());
        return task;
    }

    @Override
    public Optional<ReviewTask> findById(UUID id) {
        return Optional.ofNullable(tasks.get(id));
    }

    @Override
    public Optional<ReviewTask> findByPullRequest(String repository, long pullRequestNumber) {
        UUID taskId = pullRequests.get(key(repository, pullRequestNumber));
        return taskId == null ? Optional.empty() : findById(taskId);
    }

    private String key(String repository, long pullRequestNumber) {
        return repository.toLowerCase() + "#" + pullRequestNumber;
    }
}
