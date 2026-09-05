package com.codeops.copilot.review.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface AgentRepository extends JpaRepository<AgentEntity, String> {
    Optional<AgentEntity> findByTokenHashAndActiveTrue(String tokenHash);
    List<AgentEntity> findAllByOrderByCreatedAtDesc();
}
