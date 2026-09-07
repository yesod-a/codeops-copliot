package com.codeops.copilot.review.tasks;

import com.codeops.copilot.review.agent.AgentReviewController;
import com.codeops.copilot.review.agent.CentralReviewService;
import com.codeops.copilot.review.persistence.ProjectService;
import com.codeops.copilot.review.persistence.ReviewHistoryService;
import com.codeops.copilot.review.observability.ExecutionEventStatus;
import com.codeops.copilot.review.observability.ExecutionEventType;
import com.codeops.copilot.review.observability.ReviewExecutionEventService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
@Profile("worker")
public class ReviewTaskWorker {
    private static final int MAX_GROUP_ATTEMPTS = 3;
    private final ReviewTaskRepository tasks;
    private final ReviewOutboxEventRepository outbox;
    private final CentralReviewService reviewService;
    private final ReviewHistoryService historyService;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;
    private final ReviewExecutionEventService executionEvents;
    private final MeterRegistry meters;
    private final ReviewTaskEventPublisher taskEvents;

    @Autowired
    public ReviewTaskWorker(ReviewTaskRepository tasks, ReviewOutboxEventRepository outbox,
                            CentralReviewService reviewService, ReviewHistoryService historyService,
                            ProjectService projectService, ObjectMapper objectMapper,
                            ReviewExecutionEventService executionEvents, MeterRegistry meters,
                            ReviewTaskEventPublisher taskEvents) {
        this.tasks = tasks; this.outbox = outbox; this.reviewService = reviewService;
        this.historyService = historyService; this.projectService = projectService; this.objectMapper = objectMapper;
        this.executionEvents = executionEvents; this.meters = meters;
        this.taskEvents = taskEvents;
    }

    /** Constructor retained for focused unit tests and older integrations. */
    public ReviewTaskWorker(ReviewTaskRepository tasks, ReviewOutboxEventRepository outbox,
                            CentralReviewService reviewService, ReviewHistoryService historyService,
                            ProjectService projectService, ObjectMapper objectMapper) {
        this(tasks, outbox, reviewService, historyService, projectService, objectMapper, null, null, null);
    }

    @RabbitListener(queues = ReviewQueueConfiguration.EXECUTE_QUEUE)
    @Transactional
    public void consume(String payload) {
        try {
            String taskId = objectMapper.readTree(payload).path("taskId").asText();
            if (!taskId.isBlank()) process(taskId);
        } catch (Exception ignored) {
            // A malformed message cannot be recovered; task state is never changed without a valid ID.
        }
    }

    @Transactional
    public void process(String taskId) {
        ReviewTaskEntity task = tasks.findById(taskId).orElse(null);
        if (task == null || task.getStatus().terminal() || task.isCancelRequested()) {
            if (task != null && task.isCancelRequested()) {
                task.completeCancelled();
                event(task, null, ExecutionEventType.TASK, "CANCELLED", ExecutionEventStatus.CANCELLED, null, null, (String) null, (String) null);
            }
            return;
        }
        ReviewTaskGroupEntity group = task.getGroups().stream()
                .filter(value -> value.getStatus() == ReviewTaskGroupStatus.QUEUED || value.getStatus() == ReviewTaskGroupStatus.RETRY_WAIT)
                .findFirst().orElse(null);
        if (group == null) return;
        group.start();
        task.start(group.getGroupNumber());
        publish(task, "GROUP_STARTED");
        LocalDateTime startedAt = LocalDateTime.now();
        event(task, group, ExecutionEventType.TASK, "START", ExecutionEventStatus.STARTED, startedAt, null, (String) null, (String) null);
        event(task, group, ExecutionEventType.GROUP, "START", ExecutionEventStatus.STARTED, startedAt, null, (String) null, (String) null);
        try {
            ProjectService.ProjectView project = projectService.get(task.getProjectId());
            AgentReviewController.AgentReviewRequest request = new AgentReviewController.AgentReviewRequest(
                    task.getProjectId(), task.getTitle(), task.getTriggerType(), task.getRepositoryKey(), task.getBranch(),
                    task.getHeadCommit(), task.getBaseRef(), group.getFiles().stream().map(file ->
                    new AgentReviewController.AgentFileRequest(file.getPath(), file.getGitStatus(), file.getAdditions(),
                            file.getDeletions(), file.getPatch(), file.getContentHash())).toList(), List.of());
            CentralReviewService.ReviewOutcome outcome = reviewService.review(request, project.policy().failOnSeverity(), task.getId(), group.getGroupNumber());
            recordLlmMetrics(task, group, outcome.metrics());
            String findings = objectMapper.writeValueAsString(outcome.findings());
            group.complete(findings);
            event(task, group, ExecutionEventType.GROUP, "COMPLETE", ExecutionEventStatus.SUCCESS, startedAt,
                    LocalDateTime.now(), (String) null, (String) null);
            meter("codeops_review_groups_total", "status", "success");
            task.completeGroup();
            publish(task, "GROUP_COMPLETED");
            if (task.getCompletedGroups() == task.getTotalGroups()) {
                completeTask(task, project.policy().failOnSeverity());
                publish(task, "TASK_COMPLETED");
                event(task, null, ExecutionEventType.TASK, "COMPLETE", ExecutionEventStatus.SUCCESS, startedAt,
                        LocalDateTime.now(), (String) null, (String) null);
                meter("codeops_review_tasks_total", "status", "success");
            }
            else enqueue(task.getId());
        } catch (Exception exception) {
            String message = safeMessage(exception);
            event(task, group, ExecutionEventType.GROUP, "FAIL", ExecutionEventStatus.FAILED, startedAt,
                    LocalDateTime.now(), "LLM_UNAVAILABLE", message);
            if (shouldRetryGroup(group)) {
                group.retry("LLM_UNAVAILABLE", message);
                group.queue();
                task.retryWaiting("LLM_UNAVAILABLE", message);
                task.requeue();
                publish(task, "GROUP_RETRY_WAIT");
                enqueue(task.getId());
                meter("codeops_review_retries_total", "reason", "LLM_UNAVAILABLE");
            } else {
                group.fail("LLM_UNAVAILABLE", message);
                task.fail("LLM_UNAVAILABLE", message);
                publish(task, "TASK_FAILED");
                event(task, null, ExecutionEventType.TASK, "FAIL", ExecutionEventStatus.FAILED, startedAt,
                        LocalDateTime.now(), "LLM_UNAVAILABLE", message);
                meter("codeops_review_tasks_total", "status", "failed");
            }
        }
    }

    static boolean shouldRetryGroup(ReviewTaskGroupEntity group) {
        return group.getAttemptCount() < MAX_GROUP_ATTEMPTS;
    }

    private void recordLlmMetrics(ReviewTaskEntity task, ReviewTaskGroupEntity group,
                                  CentralReviewService.ReviewMetrics metrics) {
        if (metrics == null) return;
        for (var phase : metrics.phaseDurationsMs().entrySet()) {
            event(task, group, ExecutionEventType.LLM, phase.getKey(), ExecutionEventStatus.SUCCESS,
                    null, null, metrics, null, phase.getValue());
        }
        if (metrics.phaseDurationsMs().isEmpty()) {
            event(task, group, ExecutionEventType.LLM, "REVIEW", ExecutionEventStatus.SUCCESS,
                    null, null, metrics, null, metrics.durationMs());
        }
        for (CentralReviewService.ToolCallMetric tool : metrics.toolCalls()) {
            event(task, group, ExecutionEventType.TOOL, tool.name(),
                    "SUCCESS".equalsIgnoreCase(tool.status()) ? ExecutionEventStatus.SUCCESS : ExecutionEventStatus.FAILED,
                    null, null, tool.durationMs(), tool.errorCode());
            meter("codeops_tool_calls_total", "tool", tool.name(), "status", tool.status().toLowerCase());
        }
        meter("codeops_llm_requests_total", "phase", metrics.phaseDurationsMs().isEmpty() ? "REVIEW" : "REVIEW", "model",
                metrics.model() == null ? "unknown" : metrics.model(), "status", "success");
    }

    private void event(ReviewTaskEntity task, ReviewTaskGroupEntity group, ExecutionEventType type, String operation,
                       ExecutionEventStatus status, LocalDateTime started, LocalDateTime completed,
                       String errorCode, String errorMessage) {
        if (executionEvents == null) return;
        executionEvents.record(new ReviewExecutionEventService.ExecutionEventCommand(task.getId(), task.getProjectId(),
                group == null ? null : group.getGroupNumber(), type, operation, status, started, completed,
                duration(started, completed), null, null, null, null, null, errorCode, errorMessage, null));
    }

    private void event(ReviewTaskEntity task, ReviewTaskGroupEntity group, ExecutionEventType type, String operation,
                       ExecutionEventStatus status, LocalDateTime started, LocalDateTime completed,
                       CentralReviewService.ReviewMetrics metrics, String errorMessage, Long durationOverride) {
        if (executionEvents == null) return;
        Long duration = durationOverride != null ? durationOverride : duration(started, completed);
        executionEvents.record(new ReviewExecutionEventService.ExecutionEventCommand(task.getId(), task.getProjectId(),
                group == null ? null : group.getGroupNumber(), type, operation, status, started, completed, duration,
                metrics == null ? null : metrics.model(), metrics == null ? null : metrics.inputTokens(),
                metrics == null ? null : metrics.outputTokens(), metrics == null ? null : metrics.totalTokens(),
                metrics == null ? null : metrics.estimatedCost(), null, null, errorMessage));
    }

    private void event(ReviewTaskEntity task, ReviewTaskGroupEntity group, ExecutionEventType type, String operation,
                       ExecutionEventStatus status, LocalDateTime started, LocalDateTime completed,
                       Long duration, String errorCode) {
        if (executionEvents == null) return;
        executionEvents.record(new ReviewExecutionEventService.ExecutionEventCommand(task.getId(), task.getProjectId(),
                group == null ? null : group.getGroupNumber(), type, operation, status, started, completed, duration,
                null, null, null, null, null, errorCode, null, null));
    }

    private long duration(LocalDateTime started, LocalDateTime completed) {
        return started == null || completed == null ? 0 : java.time.Duration.between(started, completed).toMillis();
    }

    private void meter(String name, String... tags) {
        if (meters != null) meters.counter(name, tags).increment();
    }

    private void publish(ReviewTaskEntity task, String type) {
        if (taskEvents != null) taskEvents.publishAfterCommit(new ReviewTaskEvent(task.getId(), type,
                task.getStatus().name(), task.getCurrentGroup(), task.getCompletedGroups(), task.getTotalGroups()));
    }

    private void completeTask(ReviewTaskEntity task, String threshold) throws Exception {
        List<AgentReviewController.AgentFindingRequest> findings = new ArrayList<>();
        for (ReviewTaskGroupEntity group : task.getGroups()) {
            findings.addAll(objectMapper.readValue(group.getFindingsJson(), new TypeReference<List<AgentReviewController.AgentFindingRequest>>() { }));
        }
        boolean blocked = CentralReviewService.isBlocked(findings, threshold);
        ReviewHistoryService.ReviewHistoryView history = historyService.saveForProject(task.getProjectId(),
                new ReviewHistoryService.SaveReviewCommand(UUID.fromString(task.getId()), null, task.getRepositoryKey(),
                        task.getTitle(), "GIT", "BASE_COMMIT", task.getBaseRef(), task.getBranch(), task.getHeadCommit(),
                        "central-agent", task.getFiles().stream().map(file -> new ReviewHistoryService.FileCommand(file.getPath(),
                        file.getGitStatus(), file.getAdditions(), file.getDeletions(), file.getPatch(), file.getContentHash())).toList(),
                        findings.stream().map(AgentReviewController.AgentFindingRequest::toCommand).toList()));
                task.complete(blocked ? ReviewTaskOutcome.BLOCKED : ReviewTaskOutcome.PASSED, history.id().toString());
    }

    private void enqueue(String taskId) {
        outbox.save(new ReviewOutboxEventEntity(UUID.randomUUID().toString(), taskId, ReviewTaskService.QUEUED_EVENT,
                "{\"taskId\":\"" + taskId + "\"}"));
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null ? "LLM review failed" : message.substring(0, Math.min(message.length(), 1000));
    }
}
