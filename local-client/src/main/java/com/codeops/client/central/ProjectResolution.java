package com.codeops.client.central;

public record ProjectResolution(Long projectId, String projectName, String repositoryKey, String role,
                                boolean reviewEnabled, boolean prePushEnabled, String failOnSeverity) {
    public static ProjectResolution unregistered() { return new ProjectResolution(null, null, null, null, false, false, "HIGH"); }
    public boolean registered() { return projectId != null; }
}
