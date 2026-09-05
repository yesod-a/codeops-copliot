package com.codeops.client.cli;

import com.codeops.client.config.ClientSettings;
import com.codeops.client.config.ClientSettingsStore;
import com.codeops.client.credential.CredentialStore;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class LoginCommand {
    private final ClientSettingsStore settingsStore;
    private final CredentialStore credentials;
    private final InputStream input;
    private final PrintWriter output;
    public LoginCommand(ClientSettingsStore settingsStore, CredentialStore credentials, InputStream input, PrintWriter output) {
        this.settingsStore = settingsStore; this.credentials = credentials; this.input = input; this.output = output;
    }
    public int run(List<String> arguments) {
        if (arguments.size() != 3 || !"--server-url".equals(arguments.get(0)) || !"--token-stdin".equals(arguments.get(2))) {
            output.println("Usage: login --server-url <url> --token-stdin"); return 2;
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            String token = reader.readLine();
            if (token == null || token.isBlank() || reader.readLine() != null) { output.println("Token input must contain exactly one non-empty line."); return 2; }
            credentials.saveToken(token);
            settingsStore.save(new ClientSettings(arguments.get(1), "127.0.0.1", 49152, 600, false));
            output.println("CodeOps Client login completed.");
            return 0;
        } catch (IOException | IllegalArgumentException exception) { output.println("Login failed: " + exception.getMessage()); return 1; }
    }
}
