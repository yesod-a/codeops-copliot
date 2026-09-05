package com.codeops.client.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

public final class CodeOpsHttpServer implements AutoCloseable {
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private final ServerSocket socket;
    private final LocalClientOperations operations;
    private final LocalClientStatus status;
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private volatile boolean running;
    private Thread acceptThread;

    public CodeOpsHttpServer(int port) throws IOException { this(port, null, new LocalClientStatus(false, false)); }
    public CodeOpsHttpServer(int port, LocalClientOperations operations) throws IOException { this(port, operations, new LocalClientStatus(operations != null, false)); }
    public CodeOpsHttpServer(int port, LocalClientOperations operations, LocalClientStatus status) throws IOException {
        this.operations = operations;
        this.status = status;
        socket = new ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"));
    }
    public URI baseUri() { return URI.create("http://127.0.0.1:" + socket.getLocalPort()); }
    public void start() {
        if (running) throw new IllegalStateException("Server is already started");
        running = true;
        acceptThread = Thread.ofVirtual().name("codeops-local-client-accept").start(this::acceptLoop);
    }
    private void acceptLoop() {
        while (running) {
            try {
                Socket connection = socket.accept();
                Thread.ofVirtual().start(() -> handle(connection));
            }
            catch (IOException exception) { if (running) throw new IllegalStateException("Local Client listener failed", exception); }
        }
    }
    private void handle(Socket connection) {
        try (connection) {
            try {
                if (!connection.getInetAddress().isLoopbackAddress()) { write(connection, 403, "{\"error\":\"loopback required\"}"); return; }
                Request request = read(connection);
                if ("GET".equals(request.method) && "/health".equals(request.path)) { write(connection, 200, "{\"status\":\"UP\"}"); return; }
                if ("GET".equals(request.method) && "/v1/status".equals(request.path)) {
                    writeJson(connection, 200, Map.of("status", "UP", "configuration", status.configuration(), "credential", status.credential()));
                    return;
                }
                if ("POST".equals(request.method) && "/v1/repositories/resolve".equals(request.path)) {
                    RepositoryResolveRequest resolutionRequest = mapper.readValue(request.body, RepositoryResolveRequest.class);
                    if (operations == null) { write(connection, 503, "{\"error\":\"client is not configured\"}"); return; }
                    writeJson(connection, 200, operations.resolve(java.nio.file.Path.of(resolutionRequest.repositoryPath()), resolutionRequest.remoteName())); return;
                }
                if ("POST".equals(request.method) && "/v1/reviews/pre-push".equals(request.path)) {
                    ReviewRequest reviewRequest = mapper.readValue(request.body, ReviewRequest.class);
                    if (operations == null) { write(connection, 503, "{\"error\":\"client is not configured\"}"); return; }
                    writeJson(connection, 200, operations.reviewPrePush(java.nio.file.Path.of(reviewRequest.repositoryPath()), reviewRequest.remoteName(), reviewRequest.updates())); return;
                }
                if ("POST".equals(request.method) && "/v1/reviews/pre-commit".equals(request.path)) {
                    RepositoryResolveRequest reviewRequest = mapper.readValue(request.body, RepositoryResolveRequest.class);
                    if (operations == null) { write(connection, 503, "{\"error\":\"client is not configured\"}"); return; }
                    writeJson(connection, 200, operations.reviewPreCommit(java.nio.file.Path.of(reviewRequest.repositoryPath()), reviewRequest.remoteName())); return;
                }
                if ("POST".equals(request.method) && "/v1/reviews/post-merge".equals(request.path)) {
                    RepositoryResolveRequest reviewRequest = mapper.readValue(request.body, RepositoryResolveRequest.class);
                    if (operations == null) { write(connection, 503, "{\"error\":\"client is not configured\"}"); return; }
                    writeJson(connection, 200, operations.reviewPostMerge(java.nio.file.Path.of(reviewRequest.repositoryPath()), reviewRequest.remoteName())); return;
                }
                write(connection, 404, "{\"error\":\"not found\"}");
            } catch (BadRequest | JsonProcessingException | IllegalArgumentException exception) {
                write(connection, 400, "{\"error\":\"invalid request\"}");
            } catch (Exception exception) {
                write(connection, 500, "{\"error\":\"internal error\"}");
            }
        } catch (IOException ignored) { }
    }
    private Request read(Socket connection) throws IOException, BadRequest {
        BufferedInputStream input = new BufferedInputStream(connection.getInputStream());
        String headerText = readHeaders(input);
        String[] lines = headerText.split("\\r\\n");
        String[] start = lines[0].split(" ");
        if (start.length != 3 || !"HTTP/1.1".equals(start[2])) throw new BadRequest();
        int contentLength = 0;
        for (int i = 1; i < lines.length; i++) {
            int separator = lines[i].indexOf(':');
            if (separator <= 0) throw new BadRequest();
            if ("content-length".equals(lines[i].substring(0, separator).trim().toLowerCase(Locale.ROOT))) {
                try { contentLength = Integer.parseInt(lines[i].substring(separator + 1).trim()); } catch (NumberFormatException exception) { throw new BadRequest(); }
            }
        }
        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) throw new BadRequest();
        byte[] body = input.readNBytes(contentLength);
        if (body.length != contentLength) throw new BadRequest();
        return new Request(start[0], start[1], body);
    }
    private String readHeaders(BufferedInputStream input) throws IOException, BadRequest {
        byte[] bytes = new byte[MAX_HEADER_BYTES]; int count = 0;
        while (count < bytes.length) {
            int next = input.read();
            if (next < 0) throw new BadRequest();
            bytes[count++] = (byte) next;
            if (count >= 4 && bytes[count - 4] == '\r' && bytes[count - 3] == '\n' && bytes[count - 2] == '\r' && bytes[count - 1] == '\n') {
                return new String(bytes, 0, count - 4, StandardCharsets.US_ASCII);
            }
        }
        throw new BadRequest();
    }
    private void write(Socket connection, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String reason = switch (status) { case 200 -> "OK"; case 400 -> "Bad Request"; case 403 -> "Forbidden"; case 404 -> "Not Found"; case 503 -> "Service Unavailable"; default -> "Internal Server Error"; };
        try (OutputStream output = connection.getOutputStream()) {
            output.write(("HTTP/1.1 " + status + " " + reason + "\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
        }
    }
    private void writeJson(Socket connection, int status, Object payload) throws IOException { write(connection, status, mapper.writeValueAsString(payload)); }
    @Override public void close() throws IOException { running = false; socket.close(); if (acceptThread != null) acceptThread.interrupt(); }
    private record Request(String method, String path, byte[] body) { }
    private static final class BadRequest extends Exception { }
}
