package com.codeops.copilot.review.tasks;

import com.codeops.copilot.review.agent.AgentReviewController;
import com.codeops.copilot.review.agent.CentralReviewService;
import com.codeops.copilot.review.persistence.ProjectService;
import com.codeops.copilot.review.persistence.ReviewHistoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Profile("worker")
public class ReviewTaskWorker {
    private final ReviewTaskRepository tasks;
    private final ReviewOutboxEventRepository outbox;
    private final CentralReviewService reviewService;
    private final ReviewHistoryService historyService;
    private final ProjectService projectService;
    private final ObjectMapper objectMapper;

    public ReviewTaskWorker(ReviewTaskRepository tasks, ReviewOutboxEventRepository outbox,
                            CentralReviewService reviewService, ReviewHistoryService historyService,
                            ProjectService projectService, ObjectMapper objectMapper) {
        this.tasks = tasks; this.outbox = outbox; this.reviewService = reviewService;
        this.historyService = historyService; this.projectService = projectService; this.objectMapper = objectMapper;
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
            if (task != null && task.isCancelRequested()) task.completeCancelled();
            return;
        }
        ReviewTaskGroupEntity group = task.getGroups().stream()
                .filter(value -> value.getStatus() == ReviewTaskGroupStatus.QUEUED || value.getStatus() == ReviewTaskGroupStatus.RETRY_WAIT)
                .findFirst().orElse(null);
        if (group == null) return;
        group.start();
        task.start(group.getGroupNumber());
        try {
            ProjectService.ProjectView project = projectService.get(task.getProjectId());
            AgentReviewController.AgentReviewRequest request = new AgentReviewController.AgentReviewRequest(
                    task.getProjectId(), task.getTitle(), task.getTriggerType(), task.getRepositoryKey(), task.getBranch(),
                    task.getHeadCommit(), task.getBaseRef(), group.getFiles().stream().map(file ->
                    new AgentReviewController.AgentFileRequest(file.getPath(), file.getGitStatus(), file.getAdditions(),
                            file.getDeletions(), file.getPatch(), file.getContentHash())).toList(), List.of());
            CentralReviewService.ReviewOutcome outcome = reviewService.review(request, project.policy().failOnSeverity());
            String findings = objectMapper.writeValueAsString(outcome.findings());
            group.complete(findings);
            task.completeGroup();
            if (task.getCompletedGroups() == task.getTotalGroups()) completeTask(task, project.policy().failOnSeverity());
            else enqueue(task.getId());
        } catch (Exception exception) {
            String message = safeMessage(exception);
            if (task.getRetryCount() < 3) {
                group.retry("LLM_UNAVAILABLE", message);
                group.queue();
                task.retryWaiting("LLM_UNAVAILABLE", message);
                task.requeue();
                enqueue(task.getId());
            } else {
                group.fail("LLM_UNAVAILABLE", message);
                task.fail("LLM_UNAVAILABLE", message);
            }
        }
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
