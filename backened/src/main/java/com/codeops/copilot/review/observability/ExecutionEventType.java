package com.codeops.copilot.review.observability;

/** A bounded set of operations emitted while a review task is executed. */
public enum ExecutionEventType {
    TASK,
    GROUP,
    PLAN,
    LLM,
    TOOL
}
