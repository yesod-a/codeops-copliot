package com.codeops.client.central;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class HttpCentralApiClient implements CentralApiClient {
    private final URI baseUri;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public HttpCentralApiClient(URI baseUri, Duration timeout) {
        this.baseUri = baseUri; this.timeout = timeout;
    }
    @Override public ProjectResolution resolveProject(String token, String remoteUrl) throws IOException, InterruptedException {
        return post(token, "/api/client/projects/resolve", Map.of("remoteUrl", remoteUrl), ProjectResolution.class);
    }
    @Override public CentralReviewResponse submitReview(String token, CentralReviewRequest request) throws IOException, InterruptedException {
        return post(token, "/api/agent/reviews", request, CentralReviewResponse.class);
    }
    private <T> T post(String token, String path, Object payload, Class<T> responseType) throws IOException, InterruptedException {
        if (token == null || token.isBlank()) throw new IOException("CodeOps Agent Token is unavailable");
        String body = mapper.writeValueAsString(payload);
        URLConnection connection = baseUri.resolve(path).toURL().openConnection();
        if (!(connection instanceof HttpURLConnection http)) throw new IOException("Central server URL must be HTTP(S)");
        int timeoutMillis = Math.toIntExact(timeout.toMillis());
        http.setConnectTimeout(timeoutMillis);
        http.setReadTimeout(timeoutMillis);
        http.setRequestMethod("POST");
        http.setDoOutput(true);
        http.setRequestProperty("Authorization", "Bearer " + token);
        http.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] requestBody = body.getBytes(StandardCharsets.UTF_8);
        http.setFixedLengthStreamingMode(requestBody.length);
        try {
            try (var output = http.getOutputStream()) { output.write(requestBody); }
            int status = http.getResponseCode();
            if (status < 200 || status >= 300) throw new CentralApiException(status);
            try (var input = http.getInputStream()) {
                return mapper.readValue(input, responseType);
            }
        } finally {
            http.disconnect();
        }
    }
}
