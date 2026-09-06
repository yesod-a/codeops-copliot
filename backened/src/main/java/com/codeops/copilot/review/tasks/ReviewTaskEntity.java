package com.codeops.copilot.review.tasks;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "review_tasks")
public class ReviewTaskEntity {
    @Id
    @Column(length = 36)
    private String id;
    @Column(name = "project_id", nullable = false)
    private long projectId;
    @Column(name = "requested_by_user_id", length = 36)
    private String requestedByUserId;
    @Column(name = "repository_key", nullable = false, length = 768)
    private String repositoryKey;
    @Column(nullable = false, length = 500)
    private String title;
    @Column(name = "trigger_type", nullable = false, length = 30)
    private String triggerType;
    @Column(length = 255)
    private String branch;
    @Column(name = "head_commit", length = 40)
    private String headCommit;
    @Column(name = "base_ref", length = 255)
    private String baseRef;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReviewTaskStatus status;
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ReviewTaskOutcome outcome;
    @Column(name = "total_groups", nullable = false)
    private int totalGroups;
    @Column(name = "completed_groups", nullable = false)
    private int completedGroups;
    @Column(name = "current_group")
    private Integer currentGroup;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;
    @Column(name = "error_code", length = 64)
    private String errorCode;
    @Column(name = "error_message")
    private String errorMessage;
    @Column(name = "review_id", length = 36)
    private String reviewId;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewTaskFileEntity> files = new ArrayList<>();
    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewTaskGroupEntity> groups = new ArrayList<>();

    protected ReviewTaskEntity() { }

    public ReviewTaskEntity(String id, long projectId, String requestedByUserId, String repositoryKey, String title,
                            String triggerType, String branch, String headCommit, String baseRef) {
        this.id = id; this.projectId = projectId; this.requestedByUserId = requestedByUserId;
        this.repositoryKey = repositoryKey; this.title = title; this.triggerType = triggerType;
        this.branch = branch; this.headCommit = headCommit; this.baseRef = baseRef;
        this.status = ReviewTaskStatus.QUEUED;
    }

    @PrePersist void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }
    public void addFile(ReviewTaskFileEntity file) { files.add(file); file.attachTo(this); }
    public void addGroup(ReviewTaskGroupEntity group) { groups.add(group); group.attachTo(this); totalGroups = groups.size(); }
    public boolean requestCancellation() {
        if (status.terminal()) return false;
        cancelRequested = true;
        if (status == ReviewTaskStatus.QUEUED || status == ReviewTaskStatus.RETRY_WAIT) completeCancelled();
        else status = ReviewTaskStatus.CANCEL_REQUESTED;
        return true;
    }
    public void start(int groupNumber) { status = ReviewTaskStatus.RUNNING; currentGroup = groupNumber; if (startedAt == null) startedAt = LocalDateTime.now(); }
    public void completeGroup() { completedGroups++; currentGroup = null; }
    public void complete(ReviewTaskOutcome finalOutcome, String finalReviewId) { status = ReviewTaskStatus.COMPLETED; outcome = finalOutcome; reviewId = finalReviewId; completedAt = LocalDateTime.now(); currentGroup = null; }
    public void fail(String code, String message) { status = ReviewTaskStatus.FAILED; errorCode = code; errorMessage = message; completedAt = LocalDateTime.now(); currentGroup = null; }
    public void retryWaiting(String code, String message) { status = ReviewTaskStatus.RETRY_WAIT; retryCount++; errorCode = code; errorMessage = message; currentGroup = null; }
    public void requeue() { if (!status.terminal() && status != ReviewTaskStatus.RETRY_WAIT) return; status = ReviewTaskStatus.QUEUED; outcome = null; errorCode = null; errorMessage = null; completedAt = null; cancelRequested = false; }
    public void completeCancelled() { status = ReviewTaskStatus.CANCELLED; completedAt = LocalDateTime.now(); currentGroup = null; }
    public String getId() { return id; } public long getProjectId() { return projectId; } public String getRequestedByUserId() { return requestedByUserId; }
    public String getRepositoryKey() { return repositoryKey; } public String getTitle() { return title; } public String getTriggerType() { return triggerType; }
    public String getBranch() { return branch; } public String getHeadCommit() { return headCommit; } public String getBaseRef() { return baseRef; }
    public ReviewTaskStatus getStatus() { return status; } public ReviewTaskOutcome getOutcome() { return outcome; } public int getTotalGroups() { return totalGroups; }
    public int getCompletedGroups() { return completedGroups; } public Integer getCurrentGroup() { return currentGroup; } public int getRetryCount() { return retryCount; }
    public boolean isCancelRequested() { return cancelRequested; } public String getErrorCode() { return errorCode; } public String getErrorMessage() { return errorMessage; }
    public String getReviewId() { return reviewId; } public LocalDateTime getCreatedAt() { return createdAt; } public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; } public List<ReviewTaskFileEntity> getFiles() { return files; } public List<ReviewTaskGroupEntity> getGroups() { return groups; }
}
