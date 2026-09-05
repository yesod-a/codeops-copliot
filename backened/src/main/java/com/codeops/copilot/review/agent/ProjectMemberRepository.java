package com.codeops.copilot.review.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMemberEntity, Long> {
    @Query("select count(m) > 0 from ProjectMemberEntity m where m.project.id = :projectId and m.user.id = :userId and m.role <> com.codeops.copilot.review.agent.ProjectMemberRole.VIEWER")
    boolean canReview(long projectId, String userId);

    @Query("select m.role from ProjectMemberEntity m where m.project.id = :projectId and m.user.id = :userId")
    Optional<ProjectMemberRole> findRole(long projectId, String userId);

    Optional<ProjectMemberEntity> findByProjectIdAndUserId(long projectId, String userId);
}
