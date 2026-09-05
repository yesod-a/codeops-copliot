package com.codeops.client.central;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CentralReviewResponse(boolean blocked, String blockReason, JsonNode review) {
    public List<JsonNode> findings() {
        JsonNode findings = review == null ? null : review.path("findings");
        if (findings == null || !findings.isArray()) return List.of();
        java.util.ArrayList<JsonNode> result = new java.util.ArrayList<>(); findings.forEach(result::add); return List.copyOf(result);
    }
}
