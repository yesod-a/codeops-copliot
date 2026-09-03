package com.codeops.copilot.review;

import com.codeops.copilot.review.git.GitRepositoryService;
import com.codeops.copilot.review.git.GitReviewException;
import com.codeops.copilot.review.git.GitScope;
import com.codeops.copilot.review.git.RepositorySnapshot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api")
public class ReviewController {
    private final ReviewService reviewService;
    private final GitRepositoryService repositoryService;
    private final AtomicLong localRunNumber = new AtomicLong(System.currentTimeMillis());

    public ReviewController(ReviewService reviewService, GitRepositoryService repositoryService) {
        this.reviewService = reviewService;
        this.repositoryService = repositoryService;
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReviewTask submit(@Valid @RequestBody SubmitReviewRequest request) {
        ReviewTask task = reviewService.submit(request.toDomain());
        reviewService.processAsync(task.id());
        return task;
    }

    @GetMapping("/reviews/{id}")
    public ReviewTask get(@PathVariable UUID id) {
        return reviewService.get(id);
    }

    @PostMapping("/reviews/{id}/process")
    public ReviewTask process(@PathVariable UUID id) {
        return reviewService.process(id);
    }

    @PostMapping("/repositories/scan")
    public RepositorySnapshot scan(@Valid @RequestBody RepositoryScanRequest request) {
        return repositoryService.scan(java.nio.file.Path.of(request.repositoryPath()), request.scope(), request.baseRef());
    }

    @PostMapping("/reviews/from-git")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReviewTask submitFromGit(@Valid @RequestBody SubmitGitReviewRequest request) {
        List<ChangedFile> files = repositoryService.readSelected(java.nio.file.Path.of(request.repositoryPath()),
                request.scope(), request.baseRef(), request.files());
        ReviewTask task = reviewService.submit(new ReviewRequest(request.repositoryPath(), nextLocalRunNumber(), request.title(), files));
        reviewService.processAsync(task.id());
        return task;
    }

    @ExceptionHandler(ReviewService.ReviewNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(ReviewService.ReviewNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(GitReviewException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse gitFailure(GitReviewException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    private long nextLocalRunNumber() {
        return localRunNumber.incrementAndGet();
    }

    public record SubmitReviewRequest(
            @NotBlank String repository,
            @Positive long pullRequestNumber,
            @NotBlank String title,
            @NotEmpty List<ChangedFileRequest> files
    ) {
        ReviewRequest toDomain() {
            return new ReviewRequest(repository, pullRequestNumber, title,
                    files.stream().map(file -> new ChangedFile(file.path(), file.content())).toList());
        }
    }

    public record ChangedFileRequest(@NotBlank String path, String content) {
        public ChangedFileRequest {
            content = content == null ? "" : content;
        }
    }

    public record RepositoryScanRequest(@NotBlank String repositoryPath, @NotNull GitScope scope, String baseRef) {
    }

    public record SubmitGitReviewRequest(
            @NotBlank String repositoryPath,
            @NotNull GitScope scope,
            String baseRef,
            @NotBlank String title,
            @NotEmpty List<@NotBlank String> files
    ) {
        public SubmitGitReviewRequest {
            files = List.copyOf(files);
        }
    }

    public record ErrorResponse(String message) {
    }
}
