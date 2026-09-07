package com.codeops.copilot.review.tasks;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewOutboxEventRepository extends JpaRepository<ReviewOutboxEventEntity, String> {
    List<ReviewOutboxEventEntity> findByPublishedAtIsNullOrderByCreatedAtAsc(Pageable pageable);
    long countByPublishedAtIsNull();
}
