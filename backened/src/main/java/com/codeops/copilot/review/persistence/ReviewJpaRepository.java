package com.codeops.copilot.review.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewJpaRepository extends JpaRepository<ReviewEntity, String> {
    Optional<ReviewEntity> findByRequestId(String requestId);
}
