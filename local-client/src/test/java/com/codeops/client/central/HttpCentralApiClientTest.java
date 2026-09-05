package com.codeops.client.central;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpCentralApiClientTest {
    private ServerSocket server;
    private Thread serverThread;
    private AtomicReference<String> body;
    private AtomicReference<String> authorization;
    private HttpCentralApiClient client;

    @BeforeEach void start() throws Exception {
        body = new AtomicReference<>(); authorization = new AtomicReference<>();
        server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        serverThread = Thread.ofVirtual().start(this::resolve);
        client = new HttpCentralApiClient(URI.create("http://127.0.0.1:" + server.getLocalPort()), Duration.ofSeconds(5));
    }
    @AfterEach void stop() throws Exception { if (server != null) server.close(); if (serverThread != null) serverThread.join(1000); }

    @Test
    void postsOnlyRemoteUrlAndBearerTokenToProjectResolution() throws Exception {
        ProjectResolution resolution = client.resolveProject("cop_token", "https://git.example.com/acme/order-service.git");

        assertThat(resolution.projectId()).isEqualTo(3L);
        assertThat(body.get()).isEqualTo("{\"remoteUrl\":\"https://git.example.com/acme/order-service.git\"}");
        assertThat(body.get()).doesNotContain("D:\\");
        assertThat(authorization.get()).isEqualTo("Bearer cop_token");
    }

    private void resolve() {
        try (Socket socket = server.accept()) {
            var input = socket.getInputStream();
            StringBuilder headers = new StringBuilder();
            int previous = -1;
            for (int next; (next = input.read()) >= 0;) {
                headers.append((char) next);
                if (previous == '\r' && next == '\n' && headers.toString().endsWith("\r\n\r\n")) break;
                previous = next;
            }
            String text = headers.toString();
            authorization.set(text.lines().filter(line -> line.startsWith("Authorization:")).findFirst().map(line -> line.substring("Authorization: ".length())).orElse(null));
            int length = text.lines().filter(line -> line.startsWith("Content-Length:")).findFirst().map(line -> Integer.parseInt(line.substring("Content-Length: ".length()))).orElse(0);
            body.set(new String(input.readNBytes(length), StandardCharsets.UTF_8));
            byte[] response = "{\"projectId\":3,\"projectName\":\"Order Service\",\"repositoryKey\":\"git.example.com/acme/order-service\",\"role\":\"REVIEWER\",\"reviewEnabled\":true,\"prePushEnabled\":true,\"failOnSeverity\":\"HIGH\"}".getBytes(StandardCharsets.UTF_8);
            socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: " + response.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(response);
        } catch (java.io.IOException ignored) {
            // The test cleanup closes the one-shot listener when no request was accepted.
        }
    }
}
