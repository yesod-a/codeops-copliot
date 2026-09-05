package com.codeops.client.install;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeops.client.config.ClientPaths;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WinSwInstallerTest {
    @Test
    void refusesInstallationWhenWinSwHasNotBeenProvisioned(@TempDir Path home) {
        var installer = new WinSwInstaller(new ClientPaths(home), Path.of("codeops-client.jar"));

        assertThatThrownBy(installer::install).hasMessageContaining("winsw.exe");
    }
}
