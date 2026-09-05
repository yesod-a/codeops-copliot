package com.codeops.client.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class JsonClientSettingsStore implements ClientSettingsStore {
    private final ClientPaths paths;
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public JsonClientSettingsStore(ClientPaths paths) { this.paths = paths; }

    @Override public Optional<ClientSettings> load() throws IOException {
        if (!Files.isRegularFile(paths.settingsFile())) return Optional.empty();
        return Optional.of(mapper.readValue(paths.settingsFile().toFile(), ClientSettings.class));
    }

    @Override public void save(ClientSettings settings) throws IOException {
        Files.createDirectories(paths.home());
        Path temporary = Files.createTempFile(paths.home(), "client", ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), settings);
            Files.move(temporary, paths.settingsFile(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
