package com.codeops.copilot.review.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_policies", uniqueConstraints = @UniqueConstraint(name = "uk_review_policies_project", columnNames = "project_id"))
public class ReviewPolicyEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "pre_commit_enabled", nullable = false)
    private boolean preCommitEnabled;

    @Column(name = "pre_push_enabled", nullable = false)
    private boolean prePushEnabled = true;

    @Column(name = "post_merge_enabled", nullable = false)
    private boolean postMergeEnabled;

    @Column(name = "fail_on_severity", nullable = false, length = 20)
    private String failOnSeverity = "HIGH";

    @Column(name = "fail_open", nullable = false)
    private boolean failOpen;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ReviewPolicyEntity() {
    }

    public ReviewPolicyEntity(ProjectEntity project) {
        this.projectId = project.getId();
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public void update(boolean enabled, boolean preCommitEnabled, boolean prePushEnabled,
                       boolean postMergeEnabled, String failOnSeverity, boolean failOpen) {
        this.enabled = enabled;
        this.preCommitEnabled = preCommitEnabled;
        this.prePushEnabled = prePushEnabled;
        this.postMergeEnabled = postMergeEnabled;
        this.failOnSeverity = failOnSeverity;
        this.failOpen = failOpen;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public boolean isEnabled() { return enabled; }
    public boolean isPreCommitEnabled() { return preCommitEnabled; }
    public boolean isPrePushEnabled() { return prePushEnabled; }
    public boolean isPostMergeEnabled() { return postMergeEnabled; }
    public String getFailOnSeverity() { return failOnSeverity; }
    public boolean isFailOpen() { return failOpen; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
