package com.codeops.client.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record ReviewResult(boolean blocked, String message, List<JsonNode> findings) {
    public ReviewResult { findings = findings == null ? List.of() : List.copyOf(findings); }
}
