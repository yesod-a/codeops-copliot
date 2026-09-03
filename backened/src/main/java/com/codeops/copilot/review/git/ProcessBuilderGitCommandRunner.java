package com.codeops.copilot.review.git;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProcessBuilderGitCommandRunner implements GitCommandRunner {
    private final ProcessFactory processFactory;

    public ProcessBuilderGitCommandRunner() {
        this((command, repository) -> new ProcessBuilder(command)
                .directory(repository.toFile())
                .redirectErrorStream(false)
                .start());
    }

    ProcessBuilderGitCommandRunner(ProcessFactory processFactory) {
        this.processFactory = processFactory;
    }

    @Override
    public GitCommandResult run(Path repository, Duration timeout, List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(arguments);
        try {
            Process process = processFactory.start(command, repository);
            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                process.waitFor();
            }
            return new GitCommandResult(completed ? process.exitValue() : -1,
                    stdout.join(), stderr.join(), !completed);
        } catch (IOException exception) {
            return new GitCommandResult(-1, "", "Unable to run Git: " + exception.getMessage(), false);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new GitCommandResult(-1, "", "Git command was interrupted", true);
        }
    }

    private CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                return "";
            }
        });
    }

    @FunctionalInterface
    interface ProcessFactory {
        Process start(List<String> command, Path repository) throws IOException;
    }
}
