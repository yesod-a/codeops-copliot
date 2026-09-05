package com.codeops.client.http;

public record RepositoryResolveRequest(String repositoryPath, String remoteName) {
    public RepositoryResolveRequest {
        if (repositoryPath == null || repositoryPath.isBlank()) throw new IllegalArgumentException("repositoryPath is required");
    }
}
