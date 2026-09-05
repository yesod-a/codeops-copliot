package com.codeops.copilot.review.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/management")
public class BootstrapController {
    private final AgentAccessService accessService;
    private final String bootstrapToken;

    public BootstrapController(AgentAccessService accessService,
                               @Value("${codeops.bootstrap-token:change-me-in-production}") String bootstrapToken) {
        this.accessService = accessService;
        this.bootstrapToken = bootstrapToken;
    }

    @PostMapping("/bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    public AgentAccessService.BootstrapResult bootstrap(
            @RequestHeader(value = "X-CodeOps-Bootstrap-Token", required = false) String suppliedToken,
            @Valid @RequestBody BootstrapRequest request) {
        if (suppliedToken == null || !java.security.MessageDigest.isEqual(
                suppliedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                bootstrapToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bootstrap token");
        }
        return accessService.bootstrapAdmin(request.username(), request.displayName(), request.password());
    }

    public record BootstrapRequest(@NotBlank String username, @NotBlank String displayName, @NotBlank @Size(min = PasswordService.MIN_PASSWORD_LENGTH, max = 256) String password) {}
}
