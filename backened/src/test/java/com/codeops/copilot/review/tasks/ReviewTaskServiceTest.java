package com.codeops.copilot.review.tasks;

import com.codeops.copilot.review.persistence.ReviewHistoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewTaskServiceTest {
    @Test
    void createsQueuedSnapshotGroupsAndOutboxEventInOneRequest() {
        ReviewTaskRepository tasks = mock(ReviewTaskRepository.class);
        ReviewOutboxEventRepository outbox = mock(ReviewOutboxEventRepository.class);
        when(tasks.save(any(ReviewTaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outbox.save(any(ReviewOutboxEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ReviewTaskService service = new ReviewTaskService(tasks, outbox);

        ReviewTaskEntity task = service.create(new ReviewTaskService.CreateTaskCommand(
                7L, "user-1", "github.com/example/repo", "Push review", "pre-push", "main", "abc", "origin/main",
                List.of(file("one.java", 20_000), file("two.java", 20_000), file("three.java", 20_000))));

        assertThat(task.getStatus()).isEqualTo(ReviewTaskStatus.QUEUED);
        assertThat(task.getFiles()).hasSize(3);
        assertThat(task.getGroups()).hasSize(2);
        assertThat(task.getTotalGroups()).isEqualTo(2);
        ArgumentCaptor<ReviewOutboxEventEntity> event = ArgumentCaptor.forClass(ReviewOutboxEventEntity.class);
        verify(outbox).save(event.capture());
        assertThat(event.getValue().getAggregateId()).isEqualTo(task.getId());
        assertThat(event.getValue().getEventType()).isEqualTo("REVIEW_TASK_QUEUED");
        assertThat(event.getValue().getPayload()).contains(task.getId());
    }

    private ReviewHistoryService.FileCommand file(String path, int size) {
        return new ReviewHistoryService.FileCommand(path, "M", 1, 0, "x".repeat(size), null);
    }
}
