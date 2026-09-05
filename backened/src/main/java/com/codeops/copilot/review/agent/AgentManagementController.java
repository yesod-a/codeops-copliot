package com.codeops.copilot.review.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management")
public class AgentManagementController {
    private final AgentAccessService accessService;

    public AgentManagementController(AgentAccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping("/users")
    public java.util.List<AgentAccessService.UserSummary> listUsers(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                      HttpSession session) {
        accessService.requireAdmin(authorization, session);
        return accessService.listUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@RequestHeader(value = "Authorization", required = false) String authorization,
                               HttpSession session,
                               @Valid @RequestBody CreateUserRequest request) {
        accessService.requireAdmin(authorization, session);
        UserEntity user = accessService.createUser(request.username(), request.displayName(), request.role(), request.password());
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    @PostMapping("/users/{userId}/agents")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentAccessService.AgentToken createAgent(@PathVariable String userId,
                                                     @RequestHeader(value = "Authorization", required = false) String authorization,
                                                     HttpSession session,
                                                     @Valid @RequestBody CreateAgentRequest request) {
        accessService.requireAdmin(authorization, session);
        return accessService.createAgent(userId, request.name());
    }

    @PutMapping("/users/{userId}")
    public AgentAccessService.UserSummary updateUser(@PathVariable String userId,
                                                      @RequestHeader(value = "Authorization", required = false) String authorization,
                                                      HttpSession session,
                                                      @Valid @RequestBody UpdateUserRequest request) {
        AgentAccessService.Principal principal = accessService.requireAdmin(authorization, session);
        return accessService.updateUser(userId, new AgentAccessService.UpdateUserCommand(
                request.displayName(), request.role(), request.active()), principal.userId());
    }

    @PatchMapping("/users/{userId}/agents/{agentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setAgentActive(@PathVariable String userId, @PathVariable String agentId,
                               @RequestHeader(value = "Authorization", required = false) String authorization,
                               HttpSession session, @Valid @RequestBody SetAgentActiveRequest request) {
        accessService.requireAdmin(authorization, session);
        accessService.setAgentActive(userId, agentId, request.active());
    }

    @PostMapping("/projects/{projectId}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantProjectAccess(@PathVariable long projectId,
                                   @RequestHeader(value = "Authorization", required = false) String authorization,
                                   HttpSession session,
                                   @Valid @RequestBody GrantAccessRequest request) {
        accessService.requireAdmin(authorization, session);
        accessService.grantProjectAccess(projectId, request.userId(), request.role());
    }

    @PutMapping("/projects/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceProjectAccess(@PathVariable long projectId, @PathVariable String userId,
                                     @RequestHeader(value = "Authorization", required = false) String authorization,
                                     HttpSession session, @Valid @RequestBody UpdateProjectMembershipRequest request) {
        accessService.requireAdmin(authorization, session);
        accessService.grantProjectAccess(projectId, userId, request.role());
    }

    @DeleteMapping("/projects/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeProjectAccess(@PathVariable long projectId, @PathVariable String userId,
                                    @RequestHeader(value = "Authorization", required = false) String authorization,
                                    HttpSession session) {
        accessService.requireAdmin(authorization, session);
        accessService.revokeProjectAccess(projectId, userId);
    }

    public record CreateUserRequest(@NotBlank String username, @NotBlank String displayName, @NotBlank @Size(min = PasswordService.MIN_PASSWORD_LENGTH, max = 256) String password, @NotNull AgentRole role) {}
    public record CreateAgentRequest(@NotBlank String name) {}
    public record GrantAccessRequest(@NotBlank String userId, @NotNull ProjectMemberRole role) {}
    public record UpdateUserRequest(@NotBlank String displayName, @NotNull AgentRole role, boolean active) {}
    public record SetAgentActiveRequest(boolean active) {}
    public record UpdateProjectMembershipRequest(@NotNull ProjectMemberRole role) {}
    public record UserView(String id, String username, String displayName, AgentRole role) {}
}
