package com.codeops.copilot.review.observability;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "review_execution_events")
public class ReviewExecutionEventEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "group_number")
    private Integer groupNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ExecutionEventType eventType;

    @Column(nullable = false, length = 60)
    private String operation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExecutionEventStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "model_name", length = 120)
    private String modelName;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "estimated_cost")
    private BigDecimal estimatedCost;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ReviewExecutionEventEntity() {
    }

    public ReviewExecutionEventEntity(ReviewExecutionEventService.ExecutionEventCommand command) {
        this.id = UUID.randomUUID().toString();
        this.taskId = command.taskId();
        this.projectId = command.projectId();
        this.groupNumber = command.groupNumber();
        this.eventType = command.eventType();
        this.operation = command.operation();
        this.status = command.status();
        this.startedAt = command.startedAt();
        this.completedAt = command.completedAt();
        this.durationMs = command.durationMs();
        this.modelName = command.modelName();
        this.inputTokens = command.inputTokens();
        this.outputTokens = command.outputTokens();
        this.totalTokens = command.totalTokens();
        this.estimatedCost = command.estimatedCost() == null ? null : BigDecimal.valueOf(command.estimatedCost());
        this.errorCode = command.errorCode();
        this.errorMessage = command.errorMessage();
        this.metadataJson = command.metadataJson();
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public String getTaskId() { return taskId; }
    public Long getProjectId() { return projectId; }
    public Integer getGroupNumber() { return groupNumber; }
    public ExecutionEventType getEventType() { return eventType; }
    public String getOperation() { return operation; }
    public ExecutionEventStatus getStatus() { return status; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public Long getDurationMs() { return durationMs; }
    public String getModelName() { return modelName; }
    public Long getInputTokens() { return inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public String getMetadataJson() { return metadataJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
