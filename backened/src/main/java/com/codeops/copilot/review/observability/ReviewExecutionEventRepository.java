package com.codeops.copilot.review.observability;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReviewExecutionEventRepository extends JpaRepository<ReviewExecutionEventEntity, String> {
    List<ReviewExecutionEventEntity> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime from, LocalDateTime to);
    List<ReviewExecutionEventEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);
    long deleteByCreatedAtBefore(LocalDateTime cutoff);
}
