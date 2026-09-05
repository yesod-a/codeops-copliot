package com.codeops.client.cli;

import com.codeops.client.config.ClientSettingsStore;
import com.codeops.client.credential.CredentialStore;
import java.io.PrintWriter;
import java.util.function.BooleanSupplier;

public final class DoctorCommand {
    private final ClientSettingsStore settings;
    private final CredentialStore credentials;
    private final BooleanSupplier git;
    private final BooleanSupplier health;
    private final BooleanSupplier central;
    private final PrintWriter output;
    public DoctorCommand(ClientSettingsStore settings, CredentialStore credentials, BooleanSupplier git, BooleanSupplier health, BooleanSupplier central, PrintWriter output) {
        this.settings = settings; this.credentials = credentials; this.git = git; this.health = health; this.central = central; this.output = output;
    }
    public int run() {
        int status = new StatusCommand(settings, credentials, health, output).run();
        boolean gitAvailable = git.getAsBoolean(); boolean centralReachable = central.getAsBoolean();
        output.println("git: " + (gitAvailable ? "available" : "unavailable"));
        output.println("central: " + (centralReachable ? "reachable" : "unreachable"));
        return status == 0 && gitAvailable && centralReachable ? 0 : 1;
    }
}
