package com.codeops.client.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeops.client.central.HttpCentralApiClient;
import com.codeops.client.credential.CredentialStore;
import com.codeops.client.git.ChangeCollector;
import com.codeops.client.git.GitCommandRunner;
import com.codeops.client.git.RepositoryDiscovery;
import com.codeops.client.http.CodeOpsHttpServer;
import com.codeops.client.http.GitClientOperations;
import com.codeops.client.http.LocalClientStatus;
import com.codeops.client.review.ReviewWorkflow;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PrePushClientIntegrationTest {
    private final CentralStub central = new CentralStub();

    @AfterEach void stop() throws Exception { central.close(); }

    @Test
    void blockedCentralReviewPropagatesThroughTheLoopbackPrePushEndpoint(@TempDir Path repository) throws Exception {
        String head = initializeRepository(repository);
        central.start();
        var workflow = new ReviewWorkflow(new HttpCentralApiClient(central.baseUri(), Duration.ofSeconds(5)), new TokenStore(),
                new ChangeCollector(new GitCommandRunner()));
        var operations = new GitClientOperations(new RepositoryDiscovery(new GitCommandRunner()), workflow);
        try (var client = new CodeOpsHttpServer(0, operations, new LocalClientStatus(true, true))) {
            client.start();
            HttpURLConnection connection = (HttpURLConnection) client.baseUri().resolve("/v1/reviews/pre-push").toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            String update = "refs/heads/main " + head + " refs/heads/main 0000000000000000000000000000000000000000";
            String request = "{\"repositoryPath\":\"" + json(repository.toString()) + "\",\"remoteName\":\"origin\",\"updates\":[\"" + update + "\"]}";
            connection.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));

            assertThat(connection.getResponseCode()).isEqualTo(200);
            assertThat(new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).contains("\"blocked\":true");
            assertThat(central.resolveBody.get()).contains("https://git.example.com/acme/order-service.git").doesNotContain(repository.toString());
            assertThat(central.reviewBody.get()).contains("\"repositoryKey\":\"git.example.com/acme/order-service\"").doesNotContain(repository.toString());
        }
    }

    private static String initializeRepository(Path repository) throws Exception {
        git(repository, "init", "--quiet");
        Path hooks = Files.createDirectories(repository.resolve(".test-hooks"));
        git(repository, "config", "core.hooksPath", hooks.toString());
        git(repository, "config", "user.email", "test@example.com");
        git(repository, "config", "user.name", "Test");
        git(repository, "remote", "add", "origin", "https://git.example.com/acme/order-service.git");
        Files.writeString(repository.resolve("App.java"), "class App {}\n");
        git(repository, "add", "App.java");
        git(repository, "commit", "--quiet", "-m", "initial");
        Files.writeString(repository.resolve("App.java"), "class App { int value = 1; }\n");
        git(repository, "commit", "--quiet", "-am", "change");
        return output(repository, "rev-parse", "HEAD").trim();
    }

    private static void git(Path directory, String... arguments) throws Exception {
        Process process = new ProcessBuilder(java.util.stream.Stream.concat(java.util.stream.Stream.of("git", "-C", directory.toString()), java.util.Arrays.stream(arguments)).toList()).start();
        int exit = process.waitFor();
        String errors = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exit).withFailMessage("git %s failed: %s", String.join(" ", arguments), errors).isZero();
    }
    private static String output(Path directory, String... arguments) throws Exception {
        Process process = new ProcessBuilder(java.util.stream.Stream.concat(java.util.stream.Stream.of("git", "-C", directory.toString()), java.util.Arrays.stream(arguments)).toList()).start();
        assertThat(process.waitFor()).isZero();
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
    private static String json(String text) { return text.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static final class TokenStore implements CredentialStore {
        @Override public void saveToken(String token) { }
        @Override public Optional<String> loadToken() { return Optional.of("cop_token"); }
        @Override public boolean isAvailable() { return true; }
    }

    private static final class CentralStub implements AutoCloseable {
        private final ServerSocket socket;
        private final AtomicReference<String> resolveBody = new AtomicReference<>();
        private final AtomicReference<String> reviewBody = new AtomicReference<>();
        private Thread thread;
        CentralStub() {
            try { socket = new ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")); }
            catch (IOException exception) { throw new IllegalStateException(exception); }
        }
        java.net.URI baseUri() { return java.net.URI.create("http://127.0.0.1:" + socket.getLocalPort()); }
        void start() { thread = Thread.ofVirtual().start(() -> { try { respond(resolveBody, "{\"projectId\":3,\"projectName\":\"Order\",\"repositoryKey\":\"git.example.com/acme/order-service\",\"role\":\"REVIEWER\",\"reviewEnabled\":true,\"prePushEnabled\":true,\"failOnSeverity\":\"HIGH\"}"); respond(reviewBody, "{\"review\":{\"findings\":[]},\"blocked\":true,\"blockReason\":\"Policy blocked\"}"); } catch (IOException ignored) { } }); }
        private void respond(AtomicReference<String> body, String response) throws IOException {
            try (Socket connection = socket.accept()) {
                var input = connection.getInputStream();
                StringBuilder headers = new StringBuilder();
                while (!headers.toString().endsWith("\r\n\r\n")) headers.append((char) input.read());
                int length = headers.toString().lines().filter(line -> line.regionMatches(true, 0, "Content-Length:", 0, 15)).findFirst().map(line -> Integer.parseInt(line.substring(line.indexOf(':') + 1).trim())).orElse(0);
                body.set(new String(input.readNBytes(length), StandardCharsets.UTF_8));
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                connection.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                connection.getOutputStream().write(bytes);
            }
        }
        @Override public void close() throws Exception { socket.close(); if (thread != null) thread.join(1000); }
    }
}
