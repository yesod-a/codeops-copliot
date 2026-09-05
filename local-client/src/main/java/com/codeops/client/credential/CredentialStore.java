package com.codeops.client.credential;

import java.io.IOException;
import java.util.Optional;

public interface CredentialStore {
    void saveToken(String token) throws IOException;
    Optional<String> loadToken() throws IOException;
    boolean isAvailable();
}
