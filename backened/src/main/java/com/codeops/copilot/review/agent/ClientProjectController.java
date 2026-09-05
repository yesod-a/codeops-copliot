package com.codeops.copilot.review.agent;

import com.codeops.copilot.review.persistence.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/client/projects")
public class ClientProjectController {
    private final AgentAccessService accessService;
    private final ProjectService projectService;

    public ClientProjectController(AgentAccessService accessService, ProjectService projectService) {
        this.accessService = accessService;
        this.projectService = projectService;
    }

    @PostMapping("/resolve")
    public ClientProjectResolution resolve(@RequestHeader(value = "Authorization", required = false) String authorization,
                                           @Valid @RequestBody ResolveProjectRequest request) {
        AgentAccessService.Principal principal = accessService.authenticate(bearerToken(authorization));
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid agent token");
        }
        ProjectService.CentralProjectView project;
        try {
            project = projectService.findCentralProject(request.remoteUrl()).orElse(null);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        if (project == null) {
            return ClientProjectResolution.unregistered();
        }
        ProjectMemberRole role = accessService.projectRole(principal.userId(), project.id())
                .filter(value -> value != ProjectMemberRole.VIEWER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "The user cannot review this project"));
        return new ClientProjectResolution(project.id(), project.name(), project.repositoryKey(), role,
                project.policy().enabled(), project.policy().preCommitEnabled(), project.policy().prePushEnabled(),
                project.policy().postMergeEnabled(), project.policy().failOnSeverity(), project.policy().failOpen());
    }

    private String bearerToken(String value) {
        if (value == null || !value.startsWith("Bearer ")) return null;
        return value.substring("Bearer ".length()).trim();
    }

    public record ResolveProjectRequest(@NotBlank String remoteUrl) {
    }

    public record ClientProjectResolution(Long projectId, String projectName, String repositoryKey,
                                          ProjectMemberRole role, boolean reviewEnabled, boolean preCommitEnabled,
                                          boolean prePushEnabled, boolean postMergeEnabled, String failOnSeverity,
                                          boolean failOpen) {
        static ClientProjectResolution unregistered() {
            return new ClientProjectResolution(null, null, null, null, false, false, false, false,
                    "HIGH", false);
        }
    }
}
