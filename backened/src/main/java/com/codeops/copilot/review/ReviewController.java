package com.codeops.copilot.review;

import com.codeops.copilot.review.git.GitRepositoryService;
import com.codeops.copilot.review.git.GitReviewException;
import com.codeops.copilot.review.git.GitScope;
import com.codeops.copilot.review.git.RepositorySnapshot;
import com.codeops.copilot.review.persistence.ReviewHistoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ReviewController {
    private final ReviewHistoryService historyService;
    private final GitRepositoryService repositoryService;

    public ReviewController(ReviewHistoryService historyService, GitRepositoryService repositoryService) {
        this.historyService = historyService;
        this.repositoryService = repositoryService;
    }

    @PostMapping("/reviews")
    public ReviewHistoryService.ReviewHistoryView save(@Valid @RequestBody SaveReviewRequest request) {
        return historyService.save(request.toCommand());
    }

    @GetMapping("/reviews")
    public List<ReviewHistoryService.ReviewHistorySummary> list(
            @RequestParam(defaultValue = "20") @Min(1) int limit,
            @RequestParam(defaultValue = "0") @PositiveOrZero int offset) {
        return historyService.list(limit, offset);
    }

    @GetMapping("/reviews/{id}")
    public ReviewHistoryService.ReviewHistoryView get(@PathVariable UUID id) {
        return historyService.get(id);
    }

    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        historyService.delete(id);
    }

    @PostMapping("/repositories/scan")
    public RepositorySnapshot scan(@Valid @RequestBody RepositoryScanRequest request) {
        return repositoryService.scan(java.nio.file.Path.of(request.repositoryPath()), request.scope(), request.baseRef());
    }

    @ExceptionHandler(ReviewHistoryService.ReviewNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(ReviewHistoryService.ReviewNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(GitReviewException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse gitFailure(GitReviewException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse databaseFailure(DataAccessException exception) {
        return new ErrorResponse("评审历史数据库暂不可用");
    }

    public record SaveReviewRequest(
            @NotNull UUID requestId,
            String repositoryPath,
            String repository,
            @NotBlank String title,
            @NotBlank String sourceType,
            String scope,
            String baseRef,
            String branch,
            String headCommit,
            String modelName,
            @NotEmpty List<@Valid ReviewFileRequest> files,
            List<@Valid ReviewFindingRequest> findings
    ) {
        ReviewHistoryService.SaveReviewCommand toCommand() {
            return new ReviewHistoryService.SaveReviewCommand(
                    requestId, repositoryPath, repository, title, sourceType, scope, baseRef, branch,
                    headCommit, modelName,
                    files.stream().map(ReviewFileRequest::toCommand).toList(),
                    findings == null ? List.of() : findings.stream().map(ReviewFindingRequest::toCommand).toList());
        }
    }

    public record ReviewFileRequest(
            @NotBlank String path,
            String gitStatus,
            @PositiveOrZero int additions,
            @PositiveOrZero int deletions,
            String patch,
            String contentHash
    ) {
        public ReviewFileRequest {
            patch = patch == null ? "" : patch;
        }

        ReviewHistoryService.FileCommand toCommand() {
            return new ReviewHistoryService.FileCommand(path, gitStatus, additions, deletions, patch, contentHash);
        }
    }

    public record ReviewFindingRequest(
            @NotBlank String file,
            @NotBlank String category,
            @NotNull Severity severity,
            @Positive int line,
            @NotBlank String message,
            @NotBlank String suggestion,
            String evidence,
            @DecimalMin("0.0") @DecimalMax("1.0") double confidence
    ) {
        public ReviewFindingRequest {
            evidence = evidence == null ? "" : evidence;
        }

        ReviewHistoryService.FindingCommand toCommand() {
            return new ReviewHistoryService.FindingCommand(
                    file, category, severity, line, message, suggestion, evidence, confidence);
        }
    }

    public record RepositoryScanRequest(@NotBlank String repositoryPath, @NotNull GitScope scope, String baseRef) {
    }

    public record ErrorResponse(String message) {
    }
}
