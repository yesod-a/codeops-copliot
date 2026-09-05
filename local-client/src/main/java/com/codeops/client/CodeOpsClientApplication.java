package com.codeops.client;

import com.codeops.client.central.HttpCentralApiClient;
import com.codeops.client.cli.DoctorCommand;
import com.codeops.client.cli.LoginCommand;
import com.codeops.client.cli.StatusCommand;
import com.codeops.client.config.ClientPaths;
import com.codeops.client.config.ClientSettings;
import com.codeops.client.config.JsonClientSettingsStore;
import com.codeops.client.credential.DpapiCredentialStore;
import com.codeops.client.git.ChangeCollector;
import com.codeops.client.git.GitCommandRunner;
import com.codeops.client.git.RepositoryDiscovery;
import com.codeops.client.http.CodeOpsHttpServer;
import com.codeops.client.http.GitClientOperations;
import com.codeops.client.http.LocalClientStatus;
import com.codeops.client.install.WinSwInstaller;
import com.codeops.client.review.ReviewWorkflow;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

public final class CodeOpsClientApplication {
    private CodeOpsClientApplication() { }
    public static void main(String[] args) {
        System.exit(run(args, new PrintWriter(System.out, true)));
    }

    static int run(String[] args, PrintWriter output) {
        if (args.length == 0) return usage(output);
        if (!Arrays.asList("start", "login", "status", "doctor", "install", "uninstall").contains(args[0])) return usage(output);
        try {
            ClientPaths paths = ClientPaths.defaultPaths();
            var settings = new JsonClientSettingsStore(paths);
            var credentials = new DpapiCredentialStore(paths);
            return switch (args[0]) {
                case "login" -> new LoginCommand(settings, credentials, System.in, output).run(Arrays.asList(args).subList(1, args.length));
                case "status" -> new StatusCommand(settings, credentials, () -> loopbackHealthy(settings), output).run();
                case "doctor" -> new DoctorCommand(settings, credentials, CodeOpsClientApplication::gitAvailable,
                        () -> loopbackHealthy(settings), () -> centralReachable(settings), output).run();
                case "start" -> start(settings, credentials, output);
                case "install" -> install(paths, output);
                case "uninstall" -> uninstall(paths, output);
                default -> usage(output);
            };
        } catch (RuntimeException exception) {
            output.println("CodeOps Client failed: " + exception.getMessage());
            return 1;
        }
    }

    private static int start(JsonClientSettingsStore settings, DpapiCredentialStore credentials, PrintWriter output) {
        try {
            ClientSettings configured = settings.load().orElseThrow(() -> new IOException("Run login before start."));
            var central = new HttpCentralApiClient(URI.create(configured.serverUrl()), Duration.ofSeconds(configured.requestTimeoutSeconds()));
            var operations = new GitClientOperations(new RepositoryDiscovery(new GitCommandRunner()),
                    new ReviewWorkflow(central, credentials, new ChangeCollector(new GitCommandRunner())));
            try (var server = new CodeOpsHttpServer(configured.listenPort(), operations,
                    new LocalClientStatus(true, credentials.loadToken().isPresent()))) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> close(server)));
                server.start();
                output.println("CodeOps Local Client listening on " + server.baseUri());
                Thread.currentThread().join();
            }
            return 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            output.println("Start failed: " + exception.getMessage());
            return 1;
        }
    }

    private static int install(ClientPaths paths, PrintWriter output) {
        try { new WinSwInstaller(paths, executableJar()).install(); output.println("CodeOps Local Client service installed."); return 0; }
        catch (IOException | InterruptedException exception) { output.println("Install failed: " + exception.getMessage()); return 1; }
    }
    private static int uninstall(ClientPaths paths, PrintWriter output) {
        try { new WinSwInstaller(paths, executableJar()).uninstall(); output.println("CodeOps Local Client service removed."); return 0; }
        catch (IOException | InterruptedException exception) { output.println("Uninstall failed: " + exception.getMessage()); return 1; }
    }
    private static Path executableJar() { return Path.of(CodeOpsClientApplication.class.getProtectionDomain().getCodeSource().getLocation().getPath()); }
    private static void close(CodeOpsHttpServer server) { try { server.close(); } catch (IOException ignored) { } }
    private static int usage(PrintWriter output) { output.println("Usage: codeops-client <start|login|status|doctor|install|uninstall>"); return 2; }
    private static boolean loopbackHealthy(JsonClientSettingsStore settings) {
        try {
            Optional<ClientSettings> configured = settings.load();
            if (configured.isEmpty()) return false;
            HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + configured.get().listenPort() + "/health").toURL().openConnection();
            connection.setConnectTimeout(1000); connection.setReadTimeout(1000);
            try { return connection.getResponseCode() == 200; } finally { connection.disconnect(); }
        } catch (IOException | IllegalArgumentException exception) { return false; }
    }
    private static boolean gitAvailable() {
        try { return new ProcessBuilder("git", "--version").start().waitFor() == 0; }
        catch (IOException | InterruptedException exception) { if (exception instanceof InterruptedException) Thread.currentThread().interrupt(); return false; }
    }
    private static boolean centralReachable(JsonClientSettingsStore settings) {
        try {
            Optional<ClientSettings> configured = settings.load();
            if (configured.isEmpty()) return false;
            HttpURLConnection connection = (HttpURLConnection) URI.create(configured.get().serverUrl()).toURL().openConnection();
            connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
            try { return connection.getResponseCode() < 500; } finally { connection.disconnect(); }
        } catch (IOException | IllegalArgumentException exception) { return false; }
    }
}
