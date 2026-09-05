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
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), aiUrl, objectMapper);
    }

    public String aiUrl() { return aiUrl; }

    public ReviewOutcome review(AgentReviewController.AgentReviewRequest request, String failOnSeverity) {
        try {
            Map<String, Object> payload = Map.of(
                    "repository", request.repositoryKey() == null ? "project-" + request.projectId() : request.repositoryKey(),
                    "title", request.title(),
                    "files", request.files().stream().map(file -> Map.of("path", file.path(), "content", file.patch() == null ? "" : file.patch())).toList());
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(aiUrl))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new IllegalStateException("LLM service returned HTTP " + response.statusCode());
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
                    blocked ? "存在达到项目阻断阈值的评审问题" : null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("中央 LLM 服务不可用", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("中央 LLM 服务不可用", exception);
        }
    }

    private boolean isBlocked(List<AgentReviewController.AgentFindingRequest> findings, String threshold) {
        if (threshold == null || "OFF".equalsIgnoreCase(threshold)) return false;
        Severity minimum = Severity.valueOf(threshold.toUpperCase());
        return findings.stream().anyMatch(finding -> finding.severity().ordinal() >= minimum.ordinal());
    }

    public record ReviewOutcome(List<AgentReviewController.AgentFindingRequest> findings,
                                boolean blocked, String blockReason) {
    }
}
