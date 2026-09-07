package com.codeops.copilot.review.observability;

import java.time.LocalDateTime;
import java.util.List;

public final class ObservabilityView {
    private ObservabilityView() { }

    public record Overview(long taskCount, long successfulTaskCount, long failedTaskCount, long llmCallCount,
                           long llmTokenCount, long totalDurationMs) {
        public static Overview from(ReviewExecutionEventService.Overview source) {
            return new Overview(source.taskCount(), source.successfulTaskCount(), source.failedTaskCount(),
                    source.llmCallCount(), source.llmTokenCount(), source.totalDurationMs());
        }
    }

    public record ExecutionEvent(String eventType, String operation, String status, Integer groupNumber,
                                 LocalDateTime startedAt, LocalDateTime completedAt, Long durationMs,
                                 String modelName, Long inputTokens, Long outputTokens, Long totalTokens,
                                 java.math.BigDecimal estimatedCost, String errorCode, String errorMessage, LocalDateTime createdAt) {
        public static ExecutionEvent from(ReviewExecutionEventEntity source) {
            return new ExecutionEvent(source.getEventType().name(), source.getOperation(), source.getStatus().name(),
                    source.getGroupNumber(), source.getStartedAt(), source.getCompletedAt(), source.getDurationMs(),
                    source.getModelName(), source.getInputTokens(), source.getOutputTokens(), source.getTotalTokens(),
                    source.getEstimatedCost(), source.getErrorCode(), source.getErrorMessage(), source.getCreatedAt());
        }
    }

    public record ExecutionTimeline(String taskId, List<ExecutionEvent> events) { }
}
