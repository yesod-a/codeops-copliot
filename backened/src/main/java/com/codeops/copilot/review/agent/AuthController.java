package com.codeops.copilot.review.agent;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    static final String SESSION_USER_ID = "codeops.userId";
    private final AgentAccessService accessService;

    public AuthController(AgentAccessService accessService) { this.accessService = accessService; }

    @PostMapping("/login")
    public UserView login(@Valid @RequestBody LoginRequest request, HttpSession session) {
        UserEntity user = accessService.authenticatePassword(request.username(), request.password());
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        session.setAttribute(SESSION_USER_ID, user.getId());
        return UserView.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpSession session) { session.invalidate(); }

    @GetMapping("/me")
    public UserView me(HttpSession session) {
        Object userId = session.getAttribute(SESSION_USER_ID);
        if (userId == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        return accessService.findActiveUser(userId.toString()).map(UserView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录已失效"));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank @Size(min = PasswordService.MIN_PASSWORD_LENGTH, max = 256) String password) {}
    public record UserView(String id, String username, String displayName, AgentRole role) {
        static UserView from(UserEntity user) { return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()); }
    }
}
