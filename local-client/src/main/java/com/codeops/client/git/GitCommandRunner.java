package com.codeops.client.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GitCommandRunner {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    public GitResult run(Path workingDirectory, List<String> arguments) throws IOException, InterruptedException {
        if (arguments == null || arguments.isEmpty() || arguments.stream().anyMatch(value -> value == null || value.indexOf('\0') >= 0)) {
            throw new IllegalArgumentException("Git arguments must be non-empty and contain no NUL bytes");
        }
        List<String> command = new ArrayList<>();
        command.add("git"); command.addAll(arguments);
        Process process = new ProcessBuilder(command).directory(workingDirectory.toFile()).redirectErrorStream(true).start();
        if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IOException("Git command timed out");
        }
        return new GitResult(process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim());
    }

    public record GitResult(int exitCode, String output) { }
}
