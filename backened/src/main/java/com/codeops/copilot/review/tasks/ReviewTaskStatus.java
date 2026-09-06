package com.codeops.copilot.review.tasks;

public enum ReviewTaskStatus {
    QUEUED, RUNNING, RETRY_WAIT, CANCEL_REQUESTED, CANCELLED, COMPLETED, FAILED;

    public boolean terminal() {
        return this == CANCELLED || this == COMPLETED || this == FAILED;
    }
}
