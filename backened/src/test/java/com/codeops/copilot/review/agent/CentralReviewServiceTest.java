package com.codeops.copilot.review.agent;

import com.codeops.copilot.review.persistence.ReviewHistoryService;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CentralReviewServiceTest {
    @Test
    void usesConfiguredLlmEndpoint() {
        CentralReviewService service = new CentralReviewService(
                mock(HttpClient.class), "https://review.example/api/ai/review", null);

        assertThat(service.aiUrl()).isEqualTo("https://review.example/api/ai/review");
    }
}
