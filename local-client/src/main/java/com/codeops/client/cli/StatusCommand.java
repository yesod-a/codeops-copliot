package com.codeops.client.cli;

import com.codeops.client.config.ClientSettingsStore;
import com.codeops.client.credential.CredentialStore;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.BooleanSupplier;

public final class StatusCommand {
    private final ClientSettingsStore settings;
    private final CredentialStore credentials;
    private final BooleanSupplier health;
    private final PrintWriter output;
    public StatusCommand(ClientSettingsStore settings, CredentialStore credentials, BooleanSupplier health, PrintWriter output) {
        this.settings = settings; this.credentials = credentials; this.health = health; this.output = output;
    }
    public int run() {
        try {
            output.println("configuration: " + (settings.load().isPresent() ? "present" : "missing"));
            output.println("credential: " + (credentials.loadToken().isPresent() ? "present" : "missing"));
            output.println("service: " + (health.getAsBoolean() ? "reachable" : "unreachable"));
            return 0;
        } catch (IOException exception) { output.println("Status failed: " + exception.getMessage()); return 1; }
    }
}
