package com.codeops.client.credential;

import com.codeops.client.config.ClientPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class DpapiCredentialStore implements CredentialStore {
    private final ClientPaths paths;
    private final PowerShellDpapi dpapi;

    public DpapiCredentialStore(ClientPaths paths) { this(paths, new WindowsPowerShellDpapi()); }
    DpapiCredentialStore(ClientPaths paths, PowerShellDpapi dpapi) { this.paths = paths; this.dpapi = dpapi; }

    @Override public void saveToken(String token) throws IOException {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Agent Token must not be blank");
        if (!dpapi.isAvailable()) throw new IOException("Windows DPAPI is unavailable");
        final String protectedValue;
        try { protectedValue = dpapi.protect(token); }
        catch (UnsupportedOperationException exception) { throw new IOException(exception.getMessage(), exception); }
        Files.createDirectories(paths.home());
        Path temporary = Files.createTempFile(paths.home(), "credentials", ".tmp");
        try {
            Files.writeString(temporary, protectedValue, StandardCharsets.US_ASCII);
            Files.move(temporary, paths.credentialFile(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    @Override public Optional<String> loadToken() throws IOException {
        if (!Files.isRegularFile(paths.credentialFile())) return Optional.empty();
        if (!dpapi.isAvailable()) throw new IOException("Windows DPAPI is unavailable");
        try { return Optional.of(dpapi.unprotect(Files.readString(paths.credentialFile(), StandardCharsets.US_ASCII))); }
        catch (UnsupportedOperationException exception) { throw new IOException(exception.getMessage(), exception); }
    }

    @Override public boolean isAvailable() { return dpapi.isAvailable(); }
}
