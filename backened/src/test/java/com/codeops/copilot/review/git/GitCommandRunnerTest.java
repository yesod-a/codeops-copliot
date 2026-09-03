package com.codeops.copilot.review.git;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class GitCommandRunnerTest {
    @Test
    void passesArgumentsWithoutShellInterpolation() throws Exception {
        Path repository = Files.createTempDirectory("git-runner-");

        GitCommandResult result = new ProcessBuilderGitCommandRunner()
                .run(repository, Duration.ofSeconds(2), List.of("--version"));

        assertThat(result.timedOut()).isFalse();
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).startsWith("git version");
    }

    @Test
    void reportsTimeoutAndStopsTheProcess() throws Exception {
        NeverFinishesProcess process = new NeverFinishesProcess();
        GitCommandRunner runner = new ProcessBuilderGitCommandRunner((command, repository) -> process);

        GitCommandResult result = runner.run(Files.createTempDirectory("git-runner-timeout-"),
                Duration.ofMillis(1), List.of("status"));

        assertThat(result.timedOut()).isTrue();
        assertThat(process.destroyed).isTrue();
    }

    private static final class NeverFinishesProcess extends Process {
        private boolean destroyed;

        @Override public OutputStream getOutputStream() { return new ByteArrayOutputStream(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public int waitFor() { return 0; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) { return false; }
        @Override public int exitValue() { throw new IllegalThreadStateException(); }
        @Override public void destroy() { destroyed = true; }
        @Override public Process destroyForcibly() { destroyed = true; return this; }
        @Override public boolean isAlive() { return !destroyed; }
    }
}
