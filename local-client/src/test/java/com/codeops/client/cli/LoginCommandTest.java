package com.codeops.client.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.codeops.client.config.ClientPaths;
import com.codeops.client.config.JsonClientSettingsStore;
import com.codeops.client.credential.CredentialStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoginCommandTest {
    @Test
    void loginStoresTheTokenWithoutWritingItToClientSettings(@TempDir Path home) throws Exception {
        var settingsStore = new JsonClientSettingsStore(new ClientPaths(home));
        var credentials = new RecordingCredentialStore();
        var output = new ByteArrayOutputStream();
        int exit = new LoginCommand(settingsStore, credentials,
                new ByteArrayInputStream("cop_secret\n".getBytes(UTF_8)), new PrintWriter(output, true)).run(
                List.of("--server-url", "https://codeops.example.com", "--token-stdin"));

        assertThat(exit).isZero();
        assertThat(credentials.token).isEqualTo("cop_secret");
        assertThat(Files.readString(home.resolve("client.json"))).doesNotContain("cop_secret");
        assertThat(output.toString(UTF_8)).doesNotContain("cop_secret");
    }

    private static final class RecordingCredentialStore implements CredentialStore {
        private String token;
        @Override public void saveToken(String value) { token = value; }
        @Override public Optional<String> loadToken() { return Optional.ofNullable(token); }
        @Override public boolean isAvailable() { return true; }
    }
}
