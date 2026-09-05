package com.codeops.client.config;

public record ClientSettings(String serverUrl, String listenAddress, int listenPort,
                             int requestTimeoutSeconds, boolean failOpen) {
    public ClientSettings {
        serverUrl = ServerUrl.normalize(serverUrl);
        if (!"127.0.0.1".equals(listenAddress)) throw new IllegalArgumentException("listenAddress must be 127.0.0.1");
        if (listenPort < 1 || listenPort > 65535) throw new IllegalArgumentException("listenPort must be between 1 and 65535");
        if (requestTimeoutSeconds < 1 || requestTimeoutSeconds > 3600) throw new IllegalArgumentException("requestTimeoutSeconds must be between 1 and 3600");
    }
}
