package com.codeops.copilot.review;

import com.codeops.copilot.review.git.GitReviewException;
import com.codeops.copilot.review.persistence.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/import")
    public ProjectService.ProjectView importProject(@Valid @RequestBody ImportProjectRequest request) {
        return projectService.importProject(request.repositoryPath());
    }

    @PostMapping("/register")
    public ProjectService.CentralProjectView registerCentralProject(@Valid @RequestBody RegisterCentralProjectRequest request) {
        return projectService.registerRemoteProject(request.name(), request.remoteUrl());
    }

    @GetMapping
    public Object list(@RequestParam(required = false) Integer page,
                       @RequestParam(required = false) Integer size) {
        if (page == null && size == null) return projectService.list();
        return projectService.list(page == null ? 0 : page, size == null ? 3 : size);
    }

    @GetMapping("/policy/resolve")
    public ProjectService.ResolvedPolicy resolvePolicy(@RequestParam @NotBlank String repositoryPath) {
        return projectService.resolvePolicy(repositoryPath);
    }

    @GetMapping("/{id}")
    public ProjectService.ProjectView get(@PathVariable long id) {
        return projectService.get(id);
    }

    @PutMapping("/{id}/policy")
    public ProjectService.ProjectView updatePolicy(@PathVariable long id,
                                                    @Valid @RequestBody PolicyRequest request) {
        return projectService.updatePolicy(id, request.toCommand());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        projectService.delete(id);
    }

    @ExceptionHandler(ProjectService.ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(ProjectService.ProjectNotFoundException exception) {
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
        return new ErrorResponse("项目数据库暂不可用");
    }

    public record ImportProjectRequest(@NotBlank String repositoryPath) {
    }

    public record RegisterCentralProjectRequest(@NotBlank String name, @NotBlank String remoteUrl) {
    }

    public record PolicyRequest(
            @NotNull Boolean enabled,
            @NotNull Boolean preCommitEnabled,
            @NotNull Boolean prePushEnabled,
            @NotNull Boolean postMergeEnabled,
            @NotBlank @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL") String failOnSeverity,
            @NotNull Boolean failOpen
    ) {
        ProjectService.PolicyCommand toCommand() {
            return new ProjectService.PolicyCommand(enabled, preCommitEnabled, prePushEnabled,
                    postMergeEnabled, failOnSeverity, failOpen);
        }
    }

    public record ErrorResponse(String message) {
    }
}
