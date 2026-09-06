package com.codeops.copilot.review.tasks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "review_task_groups")
public class ReviewTaskGroupEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "task_id") private ReviewTaskEntity task;
    @Column(name = "group_number", nullable = false) private int groupNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private ReviewTaskGroupStatus status = ReviewTaskGroupStatus.QUEUED;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "findings_json", columnDefinition = "MEDIUMTEXT") private String findingsJson;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @ManyToMany
    @JoinTable(name = "review_task_group_files", joinColumns = @JoinColumn(name = "group_id"), inverseJoinColumns = @JoinColumn(name = "task_file_id"))
    private List<ReviewTaskFileEntity> files = new ArrayList<>();
    protected ReviewTaskGroupEntity() { }
    public ReviewTaskGroupEntity(int groupNumber, List<ReviewTaskFileEntity> files) { this.groupNumber = groupNumber; this.files.addAll(files); }
    void attachTo(ReviewTaskEntity value) { task = value; }
    public void start() { status = ReviewTaskGroupStatus.RUNNING; attemptCount++; startedAt = LocalDateTime.now(); }
    public void complete(String value) { status = ReviewTaskGroupStatus.COMPLETED; findingsJson = value; completedAt = LocalDateTime.now(); errorCode = null; errorMessage = null; }
    public void retry(String code, String message) { status = ReviewTaskGroupStatus.RETRY_WAIT; errorCode = code; errorMessage = message; }
    public void fail(String code, String message) { status = ReviewTaskGroupStatus.FAILED; errorCode = code; errorMessage = message; completedAt = LocalDateTime.now(); }
    public void queue() { status = ReviewTaskGroupStatus.QUEUED; }
    public void cancel() { status = ReviewTaskGroupStatus.CANCELLED; completedAt = LocalDateTime.now(); }
    public Long getId() { return id; } public int getGroupNumber() { return groupNumber; } public ReviewTaskGroupStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; } public String getFindingsJson() { return findingsJson; } public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; } public LocalDateTime getStartedAt() { return startedAt; } public LocalDateTime getCompletedAt() { return completedAt; } public List<ReviewTaskFileEntity> getFiles() { return files; }
}
