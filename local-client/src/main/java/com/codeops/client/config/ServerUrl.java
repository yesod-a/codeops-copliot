package com.codeops.client.config;

import java.net.URI;

public final class ServerUrl {
    private ServerUrl() { }

    public static String normalize(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !"/".equals(uri.getRawPath()))) {
                throw new IllegalArgumentException("serverUrl must be an HTTP(S) origin");
            }
            return new URI(uri.getScheme().toLowerCase(), null, uri.getHost().toLowerCase(), uri.getPort(), null, null, null).toString();
        } catch (RuntimeException | java.net.URISyntaxException exception) {
            throw new IllegalArgumentException("serverUrl must be an HTTP(S) origin", exception);
        }
    }
}
