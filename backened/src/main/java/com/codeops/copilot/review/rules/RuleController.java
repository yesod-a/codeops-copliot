package com.codeops.copilot.review.rules;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
@RequestMapping("/api")
public class RuleController {
    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping("/rules/global")
    public Object listGlobal(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        if (page == null && size == null) return ruleService.listGlobal();
        return ruleService.listGlobal(page == null ? 0 : page, size == null ? 10 : size);
    }

    @PostMapping("/rules/global")
    public RuleService.RuleView createGlobal(@Valid @RequestBody RuleRequest request) { return ruleService.createGlobal(request.toCommand()); }

    @PutMapping("/rules/global/{ruleId}")
    public RuleService.RuleView updateGlobal(@PathVariable long ruleId, @Valid @RequestBody RuleRequest request) {
        return ruleService.updateGlobal(ruleId, request.toCommand());
    }

    @DeleteMapping("/rules/global/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGlobal(@PathVariable long ruleId) { ruleService.deleteGlobal(ruleId); }

    @GetMapping("/projects/{projectId}/rules")
    public Object listProject(@PathVariable long projectId, @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        if (page == null && size == null) return ruleService.listProject(projectId);
        return ruleService.listProject(projectId, page == null ? 0 : page, size == null ? 10 : size);
    }

    @PostMapping("/projects/{projectId}/rules")
    public RuleService.RuleView createProject(@PathVariable long projectId, @Valid @RequestBody RuleRequest request) {
        return ruleService.createProject(projectId, request.toCommand());
    }

    @PutMapping("/projects/{projectId}/rules/{ruleId}")
    public RuleService.RuleView updateProject(@PathVariable long projectId, @PathVariable long ruleId,
                                              @Valid @RequestBody RuleRequest request) {
        return ruleService.updateProject(projectId, ruleId, request.toCommand());
    }

    @DeleteMapping("/projects/{projectId}/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProject(@PathVariable long projectId, @PathVariable long ruleId) { ruleService.deleteProject(projectId, ruleId); }

    @PostMapping("/projects/{projectId}/rules/preview")
    public RuleResolutionService.RulePreview preview(@PathVariable long projectId, @Valid @RequestBody RulePreviewRequest request) {
        return ruleService.preview(projectId, request.paths());
    }

    @ExceptionHandler({RuleValidationException.class, RuleResolutionService.ProjectNotFoundException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse invalidRequest(RuntimeException exception) { return new ErrorResponse(exception.getMessage()); }

    @ExceptionHandler(RuleService.RuleNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(RuleService.RuleNotFoundException exception) { return new ErrorResponse(exception.getMessage()); }

    public record RuleRequest(@NotBlank @Size(max = 120) String name, @NotBlank @Size(max = 30) String category,
                              @NotBlank @Size(max = 255) String pathPattern, @NotBlank String content,
                              @NotNull @Min(-100000) @jakarta.validation.constraints.Max(100000) Integer priority, @NotNull Boolean enabled) {
        RuleService.RuleCommand toCommand() {
            return new RuleService.RuleCommand(name.trim(), category.trim(), pathPattern.trim(), content.trim(), priority, enabled);
        }
    }

    public record RulePreviewRequest(@NotEmpty List<@NotBlank String> paths) {
        public RulePreviewRequest { paths = List.copyOf(paths); }
    }

    public record ErrorResponse(String message) {
    }
}
