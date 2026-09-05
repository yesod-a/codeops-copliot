package com.codeops.client.credential;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codeops.client.config.ClientPaths;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DpapiCredentialStoreTest {
    @Test
    void refusesToSaveATokenWhenCurrentUserDpapiIsUnavailable(@TempDir Path home) {
        var store = new DpapiCredentialStore(new ClientPaths(home), new UnsupportedPowerShellDpapi());

        assertThatThrownBy(() -> store.saveToken("cop_secret"))
                .hasMessageContaining("Windows DPAPI");
    }

    private static final class UnsupportedPowerShellDpapi implements PowerShellDpapi {
        @Override public String protect(String token) throws IOException {
            throw new UnsupportedOperationException("Windows DPAPI is unavailable");
        }
        @Override public String unprotect(String encrypted) throws IOException {
            throw new UnsupportedOperationException("Windows DPAPI is unavailable");
        }
        @Override public boolean isAvailable() { return false; }
    }
}
