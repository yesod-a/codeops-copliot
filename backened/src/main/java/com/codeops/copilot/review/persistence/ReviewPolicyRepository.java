package com.codeops.copilot.review.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewPolicyRepository extends JpaRepository<ReviewPolicyEntity, Long> {
    Optional<ReviewPolicyEntity> findByProjectId(Long projectId);
}
