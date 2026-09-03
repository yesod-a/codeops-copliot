package com.codeops.copilot.review.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
public class ProjectEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "repository_path", length = 768, unique = true)
    private String repositoryPath;

    @Column(name = "last_branch", length = 255)
    private String lastBranch;

    @Column(name = "last_head_commit", length = 40)
    private String lastHeadCommit;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ProjectEntity() {
    }

    public ProjectEntity(String name, String repositoryPath, String branch, String headCommit) {
        this.name = name;
        this.repositoryPath = repositoryPath;
        this.lastBranch = branch;
        this.lastHeadCommit = headCommit;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public void updateMetadata(String name, String branch, String headCommit) {
        this.name = name;
        this.lastBranch = branch;
        this.lastHeadCommit = headCommit;
    }

    public String getLastBranch() {
        return lastBranch;
    }

    public String getLastHeadCommit() {
        return lastHeadCommit;
    }
}
