package com.codeops.copilot.review.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByUsername(String username);
    List<UserEntity> findAllByOrderByCreatedAtDesc();
}
