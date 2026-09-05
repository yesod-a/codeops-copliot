package com.codeops.copilot.review.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentAccessServiceTest {
    @Test
    void hashesAndMatchesTokensWithoutStoringThePlainValue() {
        String hash = AgentAccessService.hashToken("secret-token");

        assertThat(hash).isNotEqualTo("secret-token");
        assertThat(AgentAccessService.matches("secret-token", hash)).isTrue();
        assertThat(AgentAccessService.matches("wrong-token", hash)).isFalse();
    }
}
