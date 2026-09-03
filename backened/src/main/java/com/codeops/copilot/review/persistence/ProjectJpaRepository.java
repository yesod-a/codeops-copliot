package com.codeops.copilot.review.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, Long> {
    Optional<ProjectEntity> findByRepositoryPath(String repositoryPath);

    Optional<ProjectEntity> findFirstByNameAndRepositoryPathIsNull(String name);
}
