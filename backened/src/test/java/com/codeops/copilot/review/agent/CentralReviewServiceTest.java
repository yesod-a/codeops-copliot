package com.codeops.copilot.review.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.codeops.copilot.review.persistence.ReviewHistoryService;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class CentralReviewServiceTest {
    @Test
    void usesConfiguredLlmEndpoint() {
        CentralReviewService service = new CentralReviewService(
                mock(HttpClient.class), "https://review.example/api/ai/review", null);

        assertThat(service.aiUrl()).isEqualTo("https://review.example/api/ai/review");
    }

    @Test
    void createsAnHttp11ClientForTheUvicornLlmService() {
        HttpClient.Builder builder = mock(HttpClient.Builder.class);
        when(builder.version(any())).thenReturn(builder);
        when(builder.connectTimeout(any())).thenReturn(builder);

        CentralReviewService.configureHttpClient(builder);

        verify(builder).version(HttpClient.Version.HTTP_1_1);
        verify(builder).connectTimeout(Duration.ofSeconds(10));
    }

    @Test
    void includesTheLlmValidationDetailWhenTheLlmServiceRejectsARequest() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(422);
        when(response.body()).thenReturn("{\"detail\":\"files.0.content is invalid\"}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        CentralReviewService service = new CentralReviewService(client, "http://llm.test/api/ai/review", new ObjectMapper());
        AgentReviewController.AgentReviewRequest request = new AgentReviewController.AgentReviewRequest(
                4L, "CodeOps pre-push review", "pre-push", "github.com/acme/service", "master", "head", "base",
                List.of(new AgentReviewController.AgentFileRequest("src/App.java", "M", 1, 0, "+class App {}", null)),
                List.of());

        assertThatThrownBy(() -> service.review(request, "HIGH"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 422")
                .hasMessageContaining("files.0.content is invalid");
    }

    @Test
    void allowsTheLlmServiceSixHundredSecondsToReturnAReview() throws Exception {
        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"findings\":[]}");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        CentralReviewService service = new CentralReviewService(client, "http://llm.test/api/ai/review", new ObjectMapper());
        AgentReviewController.AgentReviewRequest request = new AgentReviewController.AgentReviewRequest(
                4L, "CodeOps pre-push review", "pre-push", "github.com/acme/service", "master", "head", "base",
                List.of(new AgentReviewController.AgentFileRequest("src/App.java", "M", 1, 0, "+class App {}", null)),
                List.of());

        service.review(request, "HIGH");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requestCaptor.getValue().timeout()).contains(Duration.ofSeconds(600));
    }
}
