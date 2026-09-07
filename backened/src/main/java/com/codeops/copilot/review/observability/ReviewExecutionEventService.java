package com.codeops.copilot.review.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import com.codeops.copilot.review.tasks.ReviewTaskEvent;
import com.codeops.copilot.review.tasks.ReviewTaskEventPublisher;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class ReviewExecutionEventService {
    private static final Logger log = LoggerFactory.getLogger(ReviewExecutionEventService.class);
    private static final int MAX_ERROR_LENGTH = 1_000;
    private static final int MAX_METADATA_LENGTH = 10_000;

    private final ReviewExecutionEventRepository repository;
    private final ReviewTaskEventPublisher taskEvents;

    @Autowired
    public ReviewExecutionEventService(ReviewExecutionEventRepository repository, ReviewTaskEventPublisher taskEvents) {
        this.repository = repository;
        this.taskEvents = taskEvents;
    }

    /** Constructor retained for focused unit tests. */
    public ReviewExecutionEventService(ReviewExecutionEventRepository repository) {
        this(repository, null);
    }

    /** Persist telemetry on a best-effort basis; telemetry must never change review behavior. */
    public void record(ExecutionEventCommand command) {
        if (command == null || command.eventType() == null || command.status() == null) return;
        try {
            repository.save(new ReviewExecutionEventEntity(normalize(command)));
            if (taskEvents != null && command.taskId() != null) {
                taskEvents.publishAfterCommit(new ReviewTaskEvent(command.taskId(),
                        command.operation(), command.status().name(), command.groupNumber(), null, null));
            }
        } catch (RuntimeException ex) {
            log.warn("Unable to persist review execution event (taskId={}, type={})", command.taskId(), command.eventType(), ex);
        }
    }

    public Overview overview(LocalDateTime from, LocalDateTime to, Set<Long> projectIds) {
        if (from == null || to == null || from.isAfter(to)) return Overview.empty();
        List<ReviewExecutionEventEntity> events = repository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);
        Predicate<ReviewExecutionEventEntity> projectFilter = event -> projectIds == null
                || (event.getProjectId() != null && projectIds.contains(event.getProjectId()));
        List<ReviewExecutionEventEntity> selected = events.stream().filter(projectFilter).toList();
        Set<String> taskIds = selected.stream()
                .filter(event -> event.getEventType() == ExecutionEventType.TASK)
                .map(ReviewExecutionEventEntity::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        long successful = selected.stream().filter(event -> event.getEventType() == ExecutionEventType.TASK
                && event.getStatus() == ExecutionEventStatus.SUCCESS).map(ReviewExecutionEventEntity::getTaskId)
                .filter(Objects::nonNull).distinct().count();
        long failed = selected.stream().filter(event -> event.getEventType() == ExecutionEventType.TASK
                && event.getStatus() == ExecutionEventStatus.FAILED).map(ReviewExecutionEventEntity::getTaskId)
                .filter(Objects::nonNull).distinct().count();
        long llmCalls = selected.stream().filter(event -> event.getEventType() == ExecutionEventType.LLM).count();
        long tokens = selected.stream().filter(event -> event.getEventType() == ExecutionEventType.LLM)
                .mapToLong(event -> event.getTotalTokens() == null ? 0L : event.getTotalTokens()).sum();
        long duration = selected.stream().filter(event -> event.getEventType() != ExecutionEventType.TOOL)
                .map(ReviewExecutionEventEntity::getDurationMs).filter(Objects::nonNull).mapToLong(Long::longValue).sum();
        return new Overview(taskIds.size(), successful, failed, llmCalls, tokens, duration);
    }

    public List<ReviewExecutionEventEntity> timeline(String taskId) {
        if (taskId == null || taskId.isBlank()) return Collections.emptyList();
        return repository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    public List<ReviewExecutionEventEntity> events(LocalDateTime from, LocalDateTime to, Set<Long> projectIds) {
        if (from == null || to == null || from.isAfter(to)) return List.of();
        return repository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to).stream()
                .filter(event -> projectIds == null
                        || (event.getProjectId() != null && projectIds.contains(event.getProjectId())))
                .toList();
    }

    private ExecutionEventCommand normalize(ExecutionEventCommand command) {
        return new ExecutionEventCommand(command.taskId(), command.projectId(), command.groupNumber(), command.eventType(),
                cap(command.operation(), 60), command.status(), command.startedAt(), command.completedAt(), command.durationMs(),
                cap(command.modelName(), 120), command.inputTokens(), command.outputTokens(), command.totalTokens(), command.estimatedCost(),
                cap(command.errorCode(), 80), cap(command.errorMessage(), MAX_ERROR_LENGTH), cap(command.metadataJson(), MAX_METADATA_LENGTH));
    }

    private static String cap(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }

    public record ExecutionEventCommand(String taskId, Long projectId, Integer groupNumber, ExecutionEventType eventType,
                                        String operation, ExecutionEventStatus status, LocalDateTime startedAt,
                                        LocalDateTime completedAt, Long durationMs, String modelName, Long inputTokens,
                                        Long outputTokens, Long totalTokens, Double estimatedCost, String errorCode,
                                        String errorMessage, String metadataJson) {
        /** Compatibility constructor for callers that only provide an error message. */
        public ExecutionEventCommand(String taskId, Long projectId, Integer groupNumber, ExecutionEventType eventType,
                                     String operation, ExecutionEventStatus status, LocalDateTime startedAt,
                                     LocalDateTime completedAt, Long durationMs, String modelName, Long inputTokens,
                                     Long outputTokens, Long totalTokens, Double estimatedCost, String errorMessage,
                                     String metadataJson) {
            this(taskId, projectId, groupNumber, eventType, operation, status, startedAt, completedAt, durationMs,
                    modelName, inputTokens, outputTokens, totalTokens, estimatedCost, null, errorMessage, metadataJson);
        }
    }

    public record Overview(long taskCount, long successfulTaskCount, long failedTaskCount, long llmCallCount,
                           long llmTokenCount, long totalDurationMs) {
        static Overview empty() { return new Overview(0, 0, 0, 0, 0, 0); }
    }
}
