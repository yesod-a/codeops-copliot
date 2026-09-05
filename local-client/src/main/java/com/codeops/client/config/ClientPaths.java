package com.codeops.client.config;

import java.nio.file.Path;

public final class ClientPaths {
    private final Path home;

    public ClientPaths(Path home) {
        this.home = home;
    }

    public static ClientPaths defaultPaths() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            throw new IllegalStateException("LOCALAPPDATA is required for CodeOps Client settings");
        }
        return new ClientPaths(Path.of(localAppData, "CodeOps"));
    }

    public Path home() { return home; }
    public Path settingsFile() { return home.resolve("client.json"); }
    public Path credentialFile() { return home.resolve("credentials.dat"); }
    public Path repositoryCacheDirectory() { return home.resolve("repositories"); }
    public Path serviceDirectory() { return home.resolve("service"); }
}
