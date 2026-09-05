package com.codeops.client.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import com.codeops.client.central.ProjectResolution;
import com.codeops.client.review.ReviewResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CodeOpsHttpServerTest {
    @Test
    void healthEndpointReportsUpOnAnEphemeralLoopbackPort() throws Exception {
        try (var server = new CodeOpsHttpServer(0)) {
            server.start();
            HttpURLConnection connection = (HttpURLConnection) server.baseUri().resolve("/health").toURL().openConnection();
            assertThat(connection.getResponseCode()).isEqualTo(200);
            assertThat(new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("{\"status\":\"UP\"}");
        }
    }

    @Test
    void resolveEndpointRejectsUnknownJsonFields() throws Exception {
        try (var server = new CodeOpsHttpServer(0)) {
            server.start();
            HttpURLConnection connection = (HttpURLConnection) server.baseUri().resolve("/v1/repositories/resolve").toURL().openConnection();
            connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
            byte[] payload = "{\"repositoryPath\":\"C:\\\\repo\",\"unexpected\":true}".getBytes(StandardCharsets.UTF_8);
            connection.getOutputStream().write(payload);
            assertThat(connection.getResponseCode()).isEqualTo(400);
        }
    }

    @Test
    void statusEndpointDoesNotExposeConfigurationSecrets() throws Exception {
        try (var server = new CodeOpsHttpServer(0, new RecordingOperations(), new LocalClientStatus(true, true))) {
            server.start();
            HttpURLConnection connection = (HttpURLConnection) server.baseUri().resolve("/v1/status").toURL().openConnection();

            assertThat(connection.getResponseCode()).isEqualTo(200);
            String body = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(body).contains("\"configuration\":\"present\"");
            assertThat(body).contains("\"credential\":\"present\"");
            assertThat(body).doesNotContain("cop_");
        }
    }

    @Test
    void preCommitEndpointDelegatesToLocalOperations() throws Exception {
        var operations = new RecordingOperations();
        try (var server = new CodeOpsHttpServer(0, operations, new LocalClientStatus(true, true))) {
            server.start();
            HttpURLConnection connection = (HttpURLConnection) server.baseUri().resolve("/v1/reviews/pre-commit").toURL().openConnection();
            connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setRequestProperty("Content-Type", "application/json");
            connection.getOutputStream().write("{\"repositoryPath\":\"C:\\\\repo\",\"remoteName\":\"origin\"}".getBytes(StandardCharsets.UTF_8));

            assertThat(connection.getResponseCode()).isEqualTo(200);
            assertThat(operations.preCommitCalls).isEqualTo(1);
            assertThat(new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).contains("\"blocked\":false");
        }
    }

    private static final class RecordingOperations implements LocalClientOperations {
        private int preCommitCalls;
        @Override public ProjectResolution resolve(Path repositoryPath, String remoteName) { return ProjectResolution.unregistered(); }
        @Override public ReviewResult reviewPrePush(Path repositoryPath, String remoteName, List<String> updates) { return new ReviewResult(false, "OK", List.of()); }
        @Override public ReviewResult reviewPreCommit(Path repositoryPath, String remoteName) { preCommitCalls++; return new ReviewResult(false, "OK", List.of()); }
        @Override public ReviewResult reviewPostMerge(Path repositoryPath, String remoteName) { return new ReviewResult(false, "OK", List.of()); }
    }
}
