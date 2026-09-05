package com.codeops.client.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonClientSettingsStoreTest {
    @Test
    void savesAndLoadsNonSecretSettingsWithANormalizedServerUrl(@TempDir Path home) throws Exception {
        var store = new JsonClientSettingsStore(new ClientPaths(home));
        var settings = new ClientSettings("https://codeops.example.com/", "127.0.0.1", 49152, 600, false);

        store.save(settings);

        assertThat(store.load()).contains(new ClientSettings(
                "https://codeops.example.com", "127.0.0.1", 49152, 600, false));
        assertThat(Files.readString(home.resolve("client.json"))).doesNotContain("token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://codeops.example.com", "https://codeops.example.com/path?x=1", "not-a-url"})
    void rejectsInvalidServerUrls(String serverUrl) {
        assertThatThrownBy(() -> new ClientSettings(serverUrl, "127.0.0.1", 49152, 600, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
