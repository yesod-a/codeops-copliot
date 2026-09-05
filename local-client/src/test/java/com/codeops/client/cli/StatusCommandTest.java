package com.codeops.client.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeops.client.config.ClientSettings;
import com.codeops.client.config.ClientSettingsStore;
import com.codeops.client.credential.CredentialStore;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class StatusCommandTest {
    @Test
    void statusReportsCredentialPresenceWithoutPrintingTheToken() {
        var output = new ByteArrayOutputStream();
        int exit = new StatusCommand(settings(), credentials("cop_secret"), () -> true, new PrintWriter(output, true)).run();

        assertThat(exit).isZero();
        assertThat(output.toString()).contains("credential: present");
        assertThat(output.toString()).doesNotContain("cop_secret");
    }

    static ClientSettingsStore settings() {
        return new ClientSettingsStore() {
            @Override public Optional<ClientSettings> load() { return Optional.of(new ClientSettings("https://codeops.example.com", "127.0.0.1", 49152, 600, false)); }
            @Override public void save(ClientSettings settings) { }
        };
    }
    static CredentialStore credentials(String token) {
        return new CredentialStore() {
            @Override public void saveToken(String value) { }
            @Override public Optional<String> loadToken() { return Optional.of(token); }
            @Override public boolean isAvailable() { return true; }
        };
    }
}
