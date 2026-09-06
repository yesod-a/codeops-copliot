package com.codeops.copilot.review.agent;

import com.codeops.copilot.review.persistence.ProjectEntity;
import com.codeops.copilot.review.persistence.ProjectJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AgentAccessService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository userRepository;
    private final AgentRepository agentRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectJpaRepository projectRepository;

    public AgentAccessService(UserRepository userRepository, AgentRepository agentRepository,
                              ProjectMemberRepository memberRepository, ProjectJpaRepository projectRepository) {
        this.userRepository = userRepository;
        this.agentRepository = agentRepository;
        this.memberRepository = memberRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Principal authenticate(String token) {
        if (token == null || token.isBlank()) return null;
        return agentRepository.findByTokenHashAndActiveTrue(hashToken(token))
                .filter(agent -> agent.getUser().isActive())
                .map(agent -> {
                    agent.touch();
                    return new Principal(agent.getUser().getId(), agent.getUser().getUsername(), agent.getUser().getRole());
                }).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean canReview(String userId, long projectId) {
        return userRepository.findById(userId).map(user -> user.getRole() == AgentRole.ADMIN
                || memberRepository.canReview(projectId, userId)).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<ProjectMemberRole> projectRole(String userId, long projectId) {
        return userRepository.findById(userId)
                .filter(UserEntity::isActive)
                .map(user -> user.getRole() == AgentRole.ADMIN
                        ? Optional.of(ProjectMemberRole.OWNER)
                        : memberRepository.findRole(projectId, userId))
                .orElse(Optional.empty());
    }

    public Principal requireAdmin(String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim() : null;
        Principal principal = authenticate(token);
        if (principal == null || principal.role() != AgentRole.ADMIN) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Administrator access required");
        }
        return principal;
    }

    public Principal requireAdmin(String authorization, HttpSession session) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length()).trim() : null;
        Principal principal = authenticate(token);
        if (principal == null && session != null) {
            Object userId = session.getAttribute(AuthController.SESSION_USER_ID);
            if (userId != null) {
                principal = findActiveUser(userId.toString())
                        .map(user -> new Principal(user.getId(), user.getUsername(), user.getRole()))
                        .orElse(null);
            }
        }
        if (principal == null || principal.role() != AgentRole.ADMIN) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Administrator access required");
        }
        return principal;
    }

    @Transactional
    public UserEntity createUser(String username, String displayName, AgentRole role, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        return userRepository.save(new UserEntity(username, displayName, role, PasswordService.hash(password)));
    }

    @Transactional
    public BootstrapResult bootstrapAdmin(String username, String displayName, String password) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.save(new UserEntity(username, displayName, AgentRole.ADMIN, PasswordService.hash(password))));
        if (user.getPasswordHash() == null) user.setPasswordHash(PasswordService.hash(password));
        return new BootstrapResult(user.getId(), createAgent(user.getId(), "bootstrap-device"));
    }

    @Transactional(readOnly = true)
    public UserEntity authenticatePassword(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(UserEntity::isActive)
                .filter(user -> PasswordService.matches(password, user.getPasswordHash()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findActiveUser(String userId) {
        return userRepository.findById(userId).filter(UserEntity::isActive);
    }

    @Transactional(readOnly = true)
    public List<Long> projectIdsFor(String userId) {
        return memberRepository.findProjectIdsByUserId(userId);
    }

    @Transactional
    public AgentToken createAgent(String userId, String name) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        String token = generateToken();
        AgentEntity agent = agentRepository.save(new AgentEntity(user, name, hashToken(token)));
        return new AgentToken(agent.getId(), name, token);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listUsers() {
        Map<String, List<AgentSummary>> agentsByUser = agentRepository.findAllByOrderByCreatedAtDesc().stream()
                .collect(Collectors.groupingBy(agent -> agent.getUser().getId(), Collectors.mapping(agent ->
                        new AgentSummary(agent.getId(), agent.getName(), agent.isActive(), agent.getCreatedAt(), agent.getLastSeenAt()), Collectors.toList())));
        Map<String, List<ProjectMembershipSummary>> membershipsByUser = memberRepository.findAll().stream()
                .collect(Collectors.groupingBy(member -> member.getUser().getId(), Collectors.mapping(member ->
                        new ProjectMembershipSummary(member.getProject().getId(), member.getProject().getName(), member.getRole()), Collectors.toList())));
        return userRepository.findAllByOrderByCreatedAtDesc().stream().map(user -> new UserSummary(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.isActive(), user.getCreatedAt(),
                agentsByUser.getOrDefault(user.getId(), List.of()), membershipsByUser.getOrDefault(user.getId(), List.of()))).toList();
    }

    @Transactional
    public UserSummary updateUser(String userId, UpdateUserCommand command, String administratorId) {
        if (userId.equals(administratorId) && (!command.active() || command.role() != AgentRole.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Administrators cannot deactivate or demote themselves");
        }
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.update(command.displayName(), command.role(), command.active());
        return new UserSummary(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.isActive(),
                user.getCreatedAt(), List.of(), List.of());
    }

    @Transactional
    public void setAgentActive(String userId, String agentId, boolean active) {
        AgentEntity agent = agentRepository.findById(agentId).orElseThrow(() -> new IllegalArgumentException("Agent not found"));
        if (!agent.getUser().getId().equals(userId)) throw new IllegalArgumentException("Agent does not belong to user");
        agent.setActive(active);
    }

    @Transactional
    public void grantProjectAccess(long projectId, String userId, ProjectMemberRole role) {
        ProjectEntity project = projectRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found"));
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        memberRepository.findByProjectIdAndUserId(projectId, userId).ifPresentOrElse(
                member -> member.setRole(role), () -> memberRepository.save(new ProjectMemberEntity(project, user, role)));
    }

    @Transactional
    public void revokeProjectAccess(long projectId, String userId) {
        memberRepository.findByProjectIdAndUserId(projectId, userId).ifPresent(memberRepository::delete);
    }

    public static String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static boolean matches(String token, String hash) {
        return token != null && hash != null && MessageDigest.isEqual(hashToken(token).getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "cop_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record Principal(String userId, String username, AgentRole role) {}
    public record AgentToken(String agentId, String name, String token) {}
    public record BootstrapResult(String userId, AgentToken agent) {}
    public record UpdateUserCommand(String displayName, AgentRole role, boolean active) {}
    public record UserSummary(String id, String username, String displayName, AgentRole role, boolean active,
                              java.time.LocalDateTime createdAt, List<AgentSummary> agents,
                              List<ProjectMembershipSummary> memberships) {}
    public record AgentSummary(String id, String name, boolean active, java.time.LocalDateTime createdAt,
                               java.time.LocalDateTime lastSeenAt) {}
    public record ProjectMembershipSummary(long projectId, String projectName, ProjectMemberRole role) {}
}
