package com.codeops.copilot.review.tasks;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.Optional;

public interface ReviewTaskRepository extends JpaRepository<ReviewTaskEntity, String> {
    Page<ReviewTaskEntity> findByProjectId(long projectId, Pageable pageable);
    Page<ReviewTaskEntity> findByProjectIdIn(Collection<Long> projectIds, Pageable pageable);
    Optional<ReviewTaskEntity> findByIdAndProjectId(String id, long projectId);

    @EntityGraph(attributePaths = {"groups"})
    Optional<ReviewTaskEntity> findWithDetailsById(String id);
}
