package com.codeops.client.http;

import java.util.List;

public record ReviewRequest(String repositoryPath, String remoteName, List<String> updates) {
    public ReviewRequest {
        if (repositoryPath == null || repositoryPath.isBlank()) throw new IllegalArgumentException("repositoryPath is required");
        updates = updates == null ? List.of() : List.copyOf(updates);
    }
}
