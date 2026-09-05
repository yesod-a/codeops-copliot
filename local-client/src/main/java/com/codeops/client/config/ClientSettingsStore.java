package com.codeops.client.config;

import java.io.IOException;
import java.util.Optional;

public interface ClientSettingsStore {
    Optional<ClientSettings> load() throws IOException;
    void save(ClientSettings settings) throws IOException;
}
