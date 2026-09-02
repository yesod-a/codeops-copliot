package com.codeops.copilot.review;

import java.util.Optional;
import java.util.UUID;

public interface ReviewTaskRepository {
    ReviewTask save(ReviewTask task);

    Optional<ReviewTask> findById(UUID id);

    Optional<ReviewTask> findByPullRequest(String repository, long pullRequestNumber);
}
