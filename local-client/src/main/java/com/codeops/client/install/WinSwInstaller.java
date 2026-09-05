package com.codeops.client.install;

import com.codeops.client.config.ClientPaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class WinSwInstaller {
    private static final String MARKER = "CodeOps Local Client WinSW configuration";
    private final ClientPaths paths;
    private final Path executableJar;

    public WinSwInstaller(ClientPaths paths, Path executableJar) {
        this.paths = paths;
        this.executableJar = executableJar.toAbsolutePath().normalize();
    }

    public void install() throws IOException, InterruptedException {
        Path wrapper = requireWrapper();
        Files.createDirectories(paths.serviceDirectory());
        Files.writeString(configurationFile(), configuration(), StandardCharsets.UTF_8);
        run(wrapper, "install");
    }

    public void uninstall() throws IOException, InterruptedException {
        Path configuration = configurationFile();
        if (!Files.isRegularFile(configuration) || !Files.readString(configuration, StandardCharsets.UTF_8).contains(MARKER)) {
            throw new IOException("Refusing to uninstall a service not managed by CodeOps");
        }
        run(requireWrapper(), "uninstall");
    }

    private Path requireWrapper() throws IOException {
        Path wrapper = paths.serviceDirectory().resolve("winsw.exe");
        if (!Files.isRegularFile(wrapper)) throw new IOException("WinSW wrapper is missing: " + wrapper.getFileName());
        return wrapper;
    }

    private void run(Path wrapper, String action) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(List.of(wrapper.toString(), action)).directory(paths.serviceDirectory().toFile()).start();
        if (process.waitFor() != 0) throw new IOException("WinSW " + action + " failed");
    }

    private Path configurationFile() { return paths.serviceDirectory().resolve("codeops-client.xml"); }
    private String configuration() {
        return """
                <!-- %s -->
                <service>
                  <id>CodeOpsLocalClient</id>
                  <name>CodeOps Local Client</name>
                  <executable>javaw</executable>
                  <arguments>-jar &quot;%s&quot; start</arguments>
                  <log mode="roll"/>
                </service>
                """.formatted(MARKER, xml(executableJar.toString()));
    }
    private String xml(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
