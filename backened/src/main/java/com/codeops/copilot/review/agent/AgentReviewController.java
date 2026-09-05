package com.codeops.copilot.review.agent;

import com.codeops.copilot.review.Severity;
import com.codeops.copilot.review.persistence.ReviewHistoryService;
import com.codeops.copilot.review.persistence.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
public class AgentReviewController {
    private final AgentAccessService accessService;
    private final ReviewHistoryService historyService;
    private final CentralReviewService centralReviewService;
    private final ProjectService projectService;

    public AgentReviewController(AgentAccessService accessService, ReviewHistoryService historyService,
                                 CentralReviewService centralReviewService, ProjectService projectService) {
        this.accessService = accessService;
        this.historyService = historyService;
        this.centralReviewService = centralReviewService;
        this.projectService = projectService;
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentReviewResponse save(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @Valid @RequestBody AgentReviewRequest request) {
        AgentAccessService.Principal principal = accessService.authenticate(bearerToken(authorization));
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid agent token");
        if (!accessService.canReview(principal.userId(), request.projectId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "The user cannot review this project");
        }
        if (!projectService.repositoryMatches(request.projectId(), request.repositoryKey())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Repository does not match this project");
        }
        ProjectService.ProjectView project = projectService.get(request.projectId());
        if (!project.policy().enabled() || !triggerEnabled(project.policy(), request.trigger())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Review trigger is disabled for this project");
        }
        CentralReviewService.ReviewOutcome outcome = centralReviewService.review(request,
                project.policy().failOnSeverity());
        ReviewHistoryService.ReviewHistoryView saved = historyService.saveForProject(
                request.projectId(), request.toCommand(outcome.findings()));
        return new AgentReviewResponse(saved, outcome.blocked(), outcome.blockReason());
    }

    private boolean triggerEnabled(ProjectService.PolicyView policy, String trigger) {
        return switch (trigger == null ? "pre-push" : trigger.toLowerCase()) {
            case "pre-commit" -> policy.preCommitEnabled();
            case "post-merge" -> policy.postMergeEnabled();
            case "pre-push" -> policy.prePushEnabled();
            default -> false;
        };
    }

    private String bearerToken(String value) {
        if (value == null || !value.startsWith("Bearer ")) return null;
        return value.substring("Bearer ".length()).trim();
    }

    public record AgentReviewRequest(
            @NotNull Long projectId,
            @NotBlank String title,
            String trigger,
            String repositoryKey,
            String branch,
            String headCommit,
            String baseRef,
            @jakarta.validation.constraints.NotEmpty List<@Valid AgentFileRequest> files,
            List<@Valid AgentFindingRequest> findings
    ) {
        public AgentReviewRequest {
            files = files == null ? List.of() : List.copyOf(files);
            findings = findings == null ? List.of() : List.copyOf(findings);
        }

        ReviewHistoryService.SaveReviewCommand toCommand(List<AgentFindingRequest> reviewedFindings) {
            return new ReviewHistoryService.SaveReviewCommand(
                    UUID.randomUUID(), null, repositoryKey, title, "GIT", "BASE_COMMIT", baseRef,
                    branch, headCommit, "central-agent", files.stream().map(AgentFileRequest::toCommand).toList(),
                    reviewedFindings.stream().map(AgentFindingRequest::toCommand).toList());
        }
    }

    public record AgentFileRequest(@NotBlank String path, String gitStatus,
                                   @PositiveOrZero int additions, @PositiveOrZero int deletions,
                                   String patch, String contentHash) {
        ReviewHistoryService.FileCommand toCommand() {
            return new ReviewHistoryService.FileCommand(path, gitStatus, additions, deletions, patch, contentHash);
        }
    }

    public record AgentFindingRequest(@NotBlank String file, @NotBlank String category, @NotNull Severity severity,
                                      @Positive int line, @NotBlank String message, @NotBlank String suggestion,
                                      String evidence, double confidence) {
        ReviewHistoryService.FindingCommand toCommand() {
            return new ReviewHistoryService.FindingCommand(file, category, severity, line, message, suggestion,
                    evidence == null ? "" : evidence, confidence);
        }
    }

    public record AgentReviewResponse(ReviewHistoryService.ReviewHistoryView review,
                                      boolean blocked, String blockReason) {
    }
}
