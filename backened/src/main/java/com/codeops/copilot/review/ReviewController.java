package com.codeops.copilot.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReviewTask submit(@Valid @RequestBody SubmitReviewRequest request) {
        ReviewTask task = reviewService.submit(request.toDomain());
        reviewService.processAsync(task.id());
        return task;
    }

    @GetMapping("/{id}")
    public ReviewTask get(@PathVariable UUID id) {
        return reviewService.get(id);
    }

    @PostMapping("/{id}/process")
    public ReviewTask process(@PathVariable UUID id) {
        return reviewService.process(id);
    }

    @ExceptionHandler(ReviewService.ReviewNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(ReviewService.ReviewNotFoundException exception) {
        return new ErrorResponse(exception.getMessage());
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

    public record ErrorResponse(String message) {
    }
}
