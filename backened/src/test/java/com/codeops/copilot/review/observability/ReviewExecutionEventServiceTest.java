package com.codeops.copilot.review.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewExecutionEventServiceTest {
    @Mock ReviewExecutionEventRepository repository;

    @Test
    void recordsAnEventWithCappedSensitiveFields() {
        ReviewExecutionEventService service = new ReviewExecutionEventService(repository);
        String longError = "x".repeat(2_000);

        service.record(new ReviewExecutionEventService.ExecutionEventCommand(
                "task-1", 7L, 2, ExecutionEventType.LLM, "REVIEW", ExecutionEventStatus.FAILED,
                LocalDateTime.of(2026, 9, 6, 10, 0), LocalDateTime.of(2026, 9, 6, 10, 0, 1),
                1_000L, "gpt-test", 10L, 20L, 30L, 0.12, longError, "{\"files\":2}"));

        ArgumentCaptor<ReviewExecutionEventEntity> captor = ArgumentCaptor.forClass(ReviewExecutionEventEntity.class);
        verify(repository).save(captor.capture());
        ReviewExecutionEventEntity saved = captor.getValue();
        assertThat(saved.getTaskId()).isEqualTo("task-1");
        assertThat(saved.getEventType()).isEqualTo(ExecutionEventType.LLM);
        assertThat(saved.getStatus()).isEqualTo(ExecutionEventStatus.FAILED);
        assertThat(saved.getErrorMessage()).hasSize(1_000);
        assertThat(saved.getMetadataJson()).isEqualTo("{\"files\":2}");
    }

    @Test
    void aggregatesTaskAndLlmMetricsForSelectedProjects() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 6, 0, 0);
        LocalDateTime to = from.plusDays(1);
        when(repository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to)).thenReturn(List.of(
                event("task-1", 7L, ExecutionEventType.TASK, ExecutionEventStatus.SUCCESS, 1_000L, null),
                event("task-2", 7L, ExecutionEventType.TASK, ExecutionEventStatus.FAILED, 3_000L, null),
                event("task-1", 7L, ExecutionEventType.LLM, ExecutionEventStatus.SUCCESS, 2_000L, 100L),
                event("task-1", 8L, ExecutionEventType.TOOL, ExecutionEventStatus.SUCCESS, 200L, null)));

        ReviewExecutionEventService service = new ReviewExecutionEventService(repository);
        ReviewExecutionEventService.Overview overview = service.overview(from, to, java.util.Set.of(7L));

        assertThat(overview.taskCount()).isEqualTo(2);
        assertThat(overview.successfulTaskCount()).isEqualTo(1);
        assertThat(overview.failedTaskCount()).isEqualTo(1);
        assertThat(overview.llmCallCount()).isEqualTo(1);
        assertThat(overview.llmTokenCount()).isEqualTo(100L);
        assertThat(overview.totalDurationMs()).isEqualTo(6_000L);
    }

    @Test
    void returnsTaskTimelineInCreationOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 10, 0);
        when(repository.findByTaskIdOrderByCreatedAtAsc("task-1")).thenReturn(List.of(
                event("task-1", 7L, ExecutionEventType.TASK, ExecutionEventStatus.SUCCESS, 10L, null),
                event("task-1", 7L, ExecutionEventType.TOOL, ExecutionEventStatus.SUCCESS, 20L, null)));

        ReviewExecutionEventService service = new ReviewExecutionEventService(repository);
        assertThat(service.timeline("task-1")).extracting(ReviewExecutionEventEntity::getEventType)
                .containsExactly(ExecutionEventType.TASK, ExecutionEventType.TOOL);
    }

    @Test
    void swallowsRepositoryFailureWhenRecordingObservability() {
        doThrow(new IllegalStateException("database down")).when(repository).save(any());
        ReviewExecutionEventService service = new ReviewExecutionEventService(repository);

        service.record(new ReviewExecutionEventService.ExecutionEventCommand(
                "task-1", 7L, null, ExecutionEventType.TASK, "START", ExecutionEventStatus.STARTED,
                null, null, null, null, null, null, null, null, null, null));
    }

    private static ReviewExecutionEventEntity event(String taskId, long projectId, ExecutionEventType type,
                                                    ExecutionEventStatus status, long duration, Long tokens) {
        return new ReviewExecutionEventEntity(new ReviewExecutionEventService.ExecutionEventCommand(
                taskId, projectId, null, type, "TEST", status, null, null, duration,
                "test-model", tokens, null, tokens, null, null, null));
    }
}
