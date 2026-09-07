package com.codeops.copilot.review.observability;

import com.codeops.copilot.review.agent.AgentAccessService;
import com.codeops.copilot.review.agent.AgentRole;
import com.codeops.copilot.review.agent.AuthController;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;
import io.micrometer.core.instrument.MeterRegistry;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {
    private final ReviewExecutionEventService events;
    private final AgentAccessService access;
    private final MeterRegistry meters;

    public ObservabilityController(ReviewExecutionEventService events, AgentAccessService access, MeterRegistry meters) {
        this.events = events;
        this.access = access;
        this.meters = meters;
    }

    @GetMapping("/timeseries")
    public List<Map<String, Object>> timeseries(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                HttpSession session, @RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) LocalDateTime from,
                                                @RequestParam(required = false) LocalDateTime to,
                                                @RequestParam(defaultValue = "tasks") String metric) {
        AgentAccessService.Principal principal = principal(authorization, session);
        Set<Long> projects = accessibleProjects(principal, projectId);
        LocalDateTime end = to == null ? LocalDateTime.now(ZoneOffset.UTC) : to;
        LocalDateTime start = from == null ? end.minusDays(7) : from;
        Map<String, long[]> buckets = new LinkedHashMap<>();
        for (ReviewExecutionEventEntity event : events.events(start, end, projects)) {
            LocalDateTime hour = event.getCreatedAt().withMinute(0).withSecond(0).withNano(0);
            String key = hour.toString();
            long[] values = buckets.computeIfAbsent(key, ignored -> new long[2]);
            if ("llm".equalsIgnoreCase(metric) && event.getEventType() == ExecutionEventType.LLM) values[0]++;
            else if (!"llm".equalsIgnoreCase(metric) && event.getEventType() == ExecutionEventType.TASK) values[0]++;
            if (event.getDurationMs() != null) values[1] += event.getDurationMs();
        }
        return buckets.entrySet().stream().map(entry -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("bucket", entry.getKey()); point.put("count", entry.getValue()[0]); point.put("durationMs", entry.getValue()[1]);
            return point;
        }).toList();
    }

    @GetMapping("/queue")
    public Map<String, Object> queue(@RequestHeader(value = "Authorization", required = false) String authorization,
                                     HttpSession session) {
        principal(authorization, session);
        double depth = meters.get("codeops_review_queue_depth").gauge() == null ? 0 : meters.get("codeops_review_queue_depth").gauge().value();
        double outbox = meters.get("codeops_outbox_pending_events").gauge() == null ? 0 : meters.get("codeops_outbox_pending_events").gauge().value();
        return Map.of("queue", "codeops.review.execute", "depth", (int) depth, "pendingOutbox", (int) outbox);
    }

    @GetMapping("/overview")
    public ObservabilityView.Overview overview(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                HttpSession session, @RequestParam(required = false) Long projectId,
                                                @RequestParam(required = false) LocalDateTime from,
                                                @RequestParam(required = false) LocalDateTime to) {
        AgentAccessService.Principal principal = principal(authorization, session);
        Set<Long> projects = accessibleProjects(principal, projectId);
        LocalDateTime end = to == null ? LocalDateTime.now(ZoneOffset.UTC) : to;
        LocalDateTime start = from == null ? end.minusDays(7) : from;
        return ObservabilityView.Overview.from(events.overview(start, end, projects));
    }

    private Set<Long> accessibleProjects(AgentAccessService.Principal principal, Long projectId) {
        if (principal.role() == AgentRole.ADMIN) return projectId == null ? null : Set.of(projectId);
        if (projectId != null && !access.canReview(principal.userId(), projectId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该项目");
        }
        List<Long> projectIds = projectId == null ? access.projectIdsFor(principal.userId()) : List.of(projectId);
        return Set.copyOf(projectIds);
    }

    private AgentAccessService.Principal principal(String authorization, HttpSession session) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
        AgentAccessService.Principal principal = access.authenticate(token);
        if (principal == null && session != null) {
            Object userId = session.getAttribute(AuthController.SESSION_USER_ID);
            if (userId != null) principal = access.findActiveUser(userId.toString()).map(user ->
                    new AgentAccessService.Principal(user.getId(), user.getUsername(), user.getRole())).orElse(null);
        }
        if (principal == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "未登录");
        return principal;
    }
}
