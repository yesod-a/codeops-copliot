package com.codeops.copilot.review.git;

public record GitCommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {
    public boolean successful() {
        return exitCode == 0 && !timedOut;
    }
}
