package com.codeops.copilot.review.persistence;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public record RemoteRepositoryIdentity(String remoteUrl, String repositoryKey, String provider) {
    public static RemoteRepositoryIdentity parse(String remoteUrl) {
        if (remoteUrl == null || remoteUrl.isBlank()) {
            throw new IllegalArgumentException("Remote URL is required");
        }

        String value = remoteUrl.trim();
        String host;
        String path;
        if (value.matches("^[^@\\s]+@[^:\\s]+:.+$")) {
            int at = value.indexOf('@');
            int colon = value.indexOf(':', at);
            host = value.substring(at + 1, colon);
            path = value.substring(colon + 1);
        } else {
            try {
                URI uri = new URI(value);
                if (uri.getHost() == null || uri.getPath() == null || uri.getPath().isBlank()) {
                    throw new IllegalArgumentException("Remote URL must identify a Git repository");
                }
                host = uri.getHost();
                path = uri.getPath();
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("Remote URL must be a valid Git remote", exception);
            }
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String normalizedPath = path.replace('\\', '/').replaceAll("^/+|/+$", "");
        if (normalizedPath.endsWith(".git")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 4);
        }
        if (normalizedPath.isBlank()) {
            throw new IllegalArgumentException("Remote URL must identify a Git repository");
        }
        String repositoryKey = normalizedHost + "/" + normalizedPath;
        return new RemoteRepositoryIdentity(value, repositoryKey, providerFor(normalizedHost));
    }

    private static String providerFor(String host) {
        if (host.equals("github.com")) return "GITHUB";
        if (host.equals("gitlab.com")) return "GITLAB";
        return "GIT";
    }
}
