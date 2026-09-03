package com.codeops.copilot.review.git;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

public interface GitCommandRunner {
    GitCommandResult run(Path repository, Duration timeout, List<String> arguments);
}
