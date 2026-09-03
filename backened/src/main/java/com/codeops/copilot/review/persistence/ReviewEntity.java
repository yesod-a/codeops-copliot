package com.codeops.copilot.review.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reviews")
public class ReviewEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "request_id", nullable = false, length = 36, unique = true)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(length = 30)
    private String scope;

    @Column(name = "base_ref", length = 255)
    private String baseRef;

    @Column(length = 255)
    private String branch;

    @Column(name = "head_commit", length = 40)
    private String headCommit;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Column(name = "finding_count", nullable = false)
    private int findingCount;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewFileEntity> files = new ArrayList<>();

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewFindingEntity> findings = new ArrayList<>();

    protected ReviewEntity() {
    }

    public ReviewEntity(String id, String requestId, ProjectEntity project, String title, String sourceType,
                        String scope, String baseRef, String branch, String headCommit, String status,
                        String modelName, int riskScore, String errorMessage) {
        this.id = id;
        this.requestId = requestId;
        this.project = project;
        this.title = title;
        this.sourceType = sourceType;
        this.scope = scope;
        this.baseRef = baseRef;
        this.branch = branch;
        this.headCommit = headCommit;
        this.status = status;
        this.modelName = modelName;
        this.riskScore = riskScore;
        this.errorMessage = errorMessage;
        this.createdAt = LocalDateTime.now();
        this.completedAt = "COMPLETED".equals(status) ? this.createdAt : null;
    }

    public void addFile(ReviewFileEntity file) {
        files.add(file);
        file.attachTo(this);
    }

    public void addFinding(ReviewFindingEntity finding) {
        findings.add(finding);
        finding.attachTo(this);
    }

    public String getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public ProjectEntity getProject() {
        return project;
    }

    public String getTitle() {
        return title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getScope() {
        return scope;
    }

    public String getBaseRef() {
        return baseRef;
    }

    public String getBranch() {
        return branch;
    }

    public String getHeadCommit() {
        return headCommit;
    }

    public String getStatus() {
        return status;
    }

    public String getModelName() {
        return modelName;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public int getFindingCount() {
        return findingCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public List<ReviewFileEntity> getFiles() {
        return files;
    }

    public List<ReviewFindingEntity> getFindings() {
        return findings;
    }

    public void setFindingCount(int findingCount) {
        this.findingCount = findingCount;
    }
}
