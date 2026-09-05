package com.codeops.client.git;

import java.nio.file.Path;

public record ChangedFile(String path, String gitStatus, int additions, int deletions, String patch) {
    public static String requireRelativePath(Path root, String path) {
        if (path == null || path.isBlank()) throw new IllegalArgumentException("Changed path must not be blank");
        Path resolved = root.resolve(path).normalize();
        if (Path.of(path).isAbsolute() || !resolved.startsWith(root)) {
            throw new IllegalArgumentException("Changed path must stay inside the repository");
        }
        return root.relativize(resolved).toString().replace('\\', '/');
    }
}
