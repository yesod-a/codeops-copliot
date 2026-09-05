package com.codeops.copilot.review.rules;

public class RuleValidationException extends RuntimeException {
    public RuleValidationException(String message) {
        super(message);
    }
}
