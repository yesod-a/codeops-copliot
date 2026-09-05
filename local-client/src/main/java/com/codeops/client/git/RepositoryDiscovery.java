package com.codeops.client.git;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public final class RepositoryDiscovery {
    private static final Pattern REMOTE_NAME = Pattern.compile("[A-Za-z0-9._/-]+");
    private final GitCommandRunner git;
    public RepositoryDiscovery(GitCommandRunner git) { this.git = git; }

    public LocalRepository discover(Path requestedPath, String remoteName) throws IOException, InterruptedException {
        if (requestedPath == null || !Files.isDirectory(requestedPath)) throw new IllegalArgumentException("repositoryPath must be an existing directory");
        if (remoteName == null || !REMOTE_NAME.matcher(remoteName).matches() || remoteName.startsWith("-") || remoteName.contains("..")) {
            throw new IllegalArgumentException("remoteName is invalid");
        }
        var rootResult = git.run(requestedPath, List.of("rev-parse", "--show-toplevel"));
        if (rootResult.exitCode() != 0 || rootResult.output().isBlank()) throw new IOException("repositoryPath is not inside a Git repository");
        Path root = Path.of(rootResult.output()).toRealPath();
        String remoteUrl = required(root, List.of("remote", "get-url", remoteName), "Git remote was not found");
        String branch = optional(root, List.of("branch", "--show-current"));
        String head = optional(root, List.of("rev-parse", "HEAD"));
        return new LocalRepository(root, remoteUrl, branch, head);
    }

    private String required(Path root, List<String> arguments, String message) throws IOException, InterruptedException {
        String output = optional(root, arguments);
        if (output.isBlank()) throw new IOException(message);
        return output;
    }
    private String optional(Path root, List<String> arguments) throws IOException, InterruptedException {
        var result = git.run(root, arguments);
        return result.exitCode() == 0 ? result.output() : "";
    }
}
