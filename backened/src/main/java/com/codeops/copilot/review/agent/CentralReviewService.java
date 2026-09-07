package com.codeops.copilot.review.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeops.copilot.review.Severity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
public class CentralReviewService {
    private final HttpClient httpClient;
    private final String aiUrl;
    private final ObjectMapper objectMapper;

    public CentralReviewService(HttpClient httpClient, String aiUrl, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.aiUrl = aiUrl;
        this.objectMapper = objectMapper;
    }

    @Autowired
    public CentralReviewService(@Value("${codeops.ai-url:http://127.0.0.1:8090/api/ai/review}") String aiUrl,
                                ObjectMapper objectMapper) {
        this(createHttpClient(), aiUrl, objectMapper);
    }

    static HttpClient createHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder();
        configureHttpClient(builder);
        return builder.build();
    }

    static void configureHttpClient(HttpClient.Builder builder) {
        builder
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10));
    }

    public String aiUrl() { return aiUrl; }

    public ReviewOutcome review(AgentReviewController.AgentReviewRequest request, String failOnSeverity) {
        return review(request, failOnSeverity, null, null);
    }

    /** Correlation-aware variant used by asynchronous workers. */
    public ReviewOutcome review(AgentReviewController.AgentReviewRequest request, String failOnSeverity,
                                String taskId, Integer groupNumber) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("repository", request.repositoryKey() == null ? "project-" + request.projectId() : request.repositoryKey());
            payload.put("title", request.title());
            payload.put("files", request.files().stream().map(file -> Map.of("path", file.path(), "content", file.patch() == null ? "" : file.patch())).toList());
            if (taskId != null && !taskId.isBlank()) payload.put("task_id", taskId);
            if (groupNumber != null) payload.put("group_number", groupNumber);
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(aiUrl))
                    .timeout(Duration.ofSeconds(600))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                String errorCode = response.headers() == null ? null
                        : response.headers().firstValue("X-CodeOps-Error-Code").orElse(null);
                String detail = response.statusCode() >= 500
                        ? " (" + (errorCode == null ? "UPSTREAM_5XX" : errorCode) + ")"
                        : ": " + summarizeError(response.body());
                throw new IllegalStateException("LLM service returned HTTP " + response.statusCode() + detail);
            }
            JsonNode findings = objectMapper.readTree(response.body()).path("findings");
            List<AgentReviewController.AgentFindingRequest> normalized = java.util.stream.StreamSupport.stream(findings.spliterator(), false)
                    .map(node -> new AgentReviewController.AgentFindingRequest(
                            node.path("file").asText(), node.path("category").asText(),
                            Severity.valueOf(node.path("severity").asText().toUpperCase()),
                            Math.max(1, node.path("line").asInt(1)), node.path("message").asText(),
                            node.path("suggestion").asText(), node.path("evidence").asText(),
                            node.path("confidence").asDouble(0.8))).toList();
            boolean blocked = isBlocked(normalized, failOnSeverity);
            return new ReviewOutcome(normalized, blocked,
                    blocked ? "存在达到项目阻断阈值的评审问题" : null,
                    parseMetrics(objectMapper.readTree(response.body()).path("metrics")));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("中央 LLM 服务不可用", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("中央 LLM 服务不可用", exception);
        }
    }

    private ReviewMetrics parseMetrics(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        List<ToolCallMetric> toolCalls = new ArrayList<>();
        node.path("tool_calls").forEach(tool -> toolCalls.add(new ToolCallMetric(
                tool.path("name").asText("unknown"), tool.path("duration_ms").asLong(0),
                tool.path("status").asText("FAILED"), tool.path("error_code").isNull() ? null : tool.path("error_code").asText())));
        Map<String, Long> phases = new HashMap<>();
        node.path("phase_durations_ms").fields().forEachRemaining(entry -> phases.put(entry.getKey(), entry.getValue().asLong(0)));
        return new ReviewMetrics(node.path("model").asText(null), node.path("duration_ms").isNumber() ? node.path("duration_ms").asLong() : null,
                nullableLong(node, "input_tokens"), nullableLong(node, "output_tokens"), nullableLong(node, "total_tokens"),
                node.path("estimated_cost").isNumber() ? node.path("estimated_cost").asDouble() : null, toolCalls, phases);
    }

    private Long nullableLong(JsonNode node, String field) {
        return node.path(field).isNumber() ? node.path(field).asLong() : null;
    }

    public static boolean isBlocked(List<AgentReviewController.AgentFindingRequest> findings, String threshold) {
        if (threshold == null || "OFF".equalsIgnoreCase(threshold)) return false;
        Severity minimum = Severity.valueOf(threshold.toUpperCase());
        return findings.stream().anyMatch(finding -> finding.severity().ordinal() >= minimum.ordinal());
    }

    private String summarizeError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return "empty response";
        String summary = responseBody.replaceAll("\\s+", " ").trim();
        return summary.substring(0, Math.min(summary.length(), 1_000));
    }

    public record ReviewOutcome(List<AgentReviewController.AgentFindingRequest> findings,
                                boolean blocked, String blockReason, ReviewMetrics metrics) {
        public ReviewOutcome(List<AgentReviewController.AgentFindingRequest> findings, boolean blocked, String blockReason) {
            this(findings, blocked, blockReason, null);
        }
    }

    public record ReviewMetrics(String model, Long durationMs, Long inputTokens, Long outputTokens, Long totalTokens,
                                Double estimatedCost, List<ToolCallMetric> toolCalls, Map<String, Long> phaseDurationsMs) { }

    public record ToolCallMetric(String name, long durationMs, String status, String errorCode) { }
}
