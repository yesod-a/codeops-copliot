package com.codeops.copilot.review.tasks;

import com.codeops.copilot.review.agent.AgentAccessService;
import com.codeops.copilot.review.agent.AgentRole;
import com.codeops.copilot.review.agent.AuthController;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/review-tasks")
public class ReviewTaskController {
    private final ReviewTaskService taskService;
    private final AgentAccessService accessService;

    public ReviewTaskController(ReviewTaskService taskService, AgentAccessService accessService) {
        this.taskService = taskService; this.accessService = accessService;
    }

    @GetMapping
    public TaskPage list(@RequestHeader(value = "Authorization", required = false) String authorization,
                         HttpSession session, @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size) {
        AgentAccessService.Principal principal = principal(authorization, session);
        List<Long> projects = principal.role() == AgentRole.ADMIN ? List.of() : accessService.projectIdsFor(principal.userId());
        Page<ReviewTaskEntity> result = principal.role() == AgentRole.ADMIN
                ? taskService.pageForAll(page, size) : taskService.pageForProjects(projects, page, size);
        return TaskPage.from(result);
    }

    @GetMapping("/{taskId}")
    public TaskView get(@PathVariable String taskId, @RequestHeader(value = "Authorization", required = false) String authorization,
                        HttpSession session) {
        AgentAccessService.Principal principal = principal(authorization, session);
        ReviewTaskEntity task = taskService.get(taskId);
        authorize(principal, task);
        return TaskView.from(task);
    }

    @PostMapping("/{taskId}/cancel")
    public TaskView cancel(@PathVariable String taskId, @RequestHeader(value = "Authorization", required = false) String authorization,
                           HttpSession session) {
        AgentAccessService.Principal principal = principal(authorization, session);
        ReviewTaskEntity task = taskService.get(taskId); authorize(principal, task);
        return TaskView.from(taskService.cancel(taskId));
    }

    @PostMapping("/{taskId}/retry")
    public TaskView retry(@PathVariable String taskId, @RequestHeader(value = "Authorization", required = false) String authorization,
                          HttpSession session) {
        AgentAccessService.Principal principal = principal(authorization, session);
        ReviewTaskEntity task = taskService.get(taskId); authorize(principal, task);
        return TaskView.from(taskService.retry(taskId));
    }

    private AgentAccessService.Principal principal(String authorization, HttpSession session) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
        AgentAccessService.Principal principal = accessService.authenticate(token);
        if (principal == null && session != null) {
            Object userId = session.getAttribute(AuthController.SESSION_USER_ID);
            if (userId != null) principal = accessService.findActiveUser(userId.toString()).map(user ->
                    new AgentAccessService.Principal(user.getId(), user.getUsername(), user.getRole())).orElse(null);
        }
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        return principal;
    }

    private void authorize(AgentAccessService.Principal principal, ReviewTaskEntity task) {
        if (principal.role() != AgentRole.ADMIN && !accessService.canReview(principal.userId(), task.getProjectId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该评审任务");
    }

    public record TaskPage(List<TaskView> items, int page, int pageSize, long total, int totalPages) {
        static TaskPage from(Page<ReviewTaskEntity> page) { return new TaskPage(page.getContent().stream().map(TaskView::from).toList(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()); }
    }
    public record TaskView(String taskId, long projectId, String title, String status, String outcome,
                           int totalGroups, int completedGroups, Integer currentGroup, int retryCount,
                           boolean cancelRequested, String errorCode, String errorMessage, String reviewId,
                           java.time.LocalDateTime createdAt, java.time.LocalDateTime startedAt, java.time.LocalDateTime completedAt) {
        static TaskView from(ReviewTaskEntity task) { return new TaskView(task.getId(), task.getProjectId(), task.getTitle(), task.getStatus().name(), task.getOutcome() == null ? null : task.getOutcome().name(), task.getTotalGroups(), task.getCompletedGroups(), task.getCurrentGroup(), task.getRetryCount(), task.isCancelRequested(), task.getErrorCode(), task.getErrorMessage(), task.getReviewId(), task.getCreatedAt(), task.getStartedAt(), task.getCompletedAt()); }
    }
}
