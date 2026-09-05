package com.codeops.copilot.review.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordServiceTest {
    @Test
    void hashesPasswordAndRejectsWrongValue() {
        String encoded = PasswordService.hash("correct-horse");
        assertThat(encoded).startsWith("pbkdf2$");
        assertThat(PasswordService.matches("correct-horse", encoded)).isTrue();
        assertThat(PasswordService.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void acceptsTheLocalDevelopmentPassword() {
        String encoded = PasswordService.hash("123456");
        assertThat(PasswordService.matches("123456", encoded)).isTrue();
    }
}
