package com.codeops.client.credential;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class WindowsPowerShellDpapi implements PowerShellDpapi {
    private static final String PROTECT = "$t=[Console]::In.ReadToEnd();$b=[Text.Encoding]::UTF8.GetBytes($t);[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::Protect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))";
    private static final String UNPROTECT = "$t=[Console]::In.ReadToEnd();$b=[Convert]::FromBase64String($t);[Text.Encoding]::UTF8.GetString([Security.Cryptography.ProtectedData]::Unprotect($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser))";

    @Override public String protect(String token) throws IOException { return run(PROTECT, token); }
    @Override public String unprotect(String encrypted) throws IOException { return run(UNPROTECT, encrypted); }
    @Override public boolean isAvailable() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }

    private String run(String script, String input) throws IOException {
        if (!isAvailable()) throw new UnsupportedOperationException("Windows DPAPI is unavailable");
        Process process = new ProcessBuilder(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)).start();
        try (var writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8)) {
            writer.write(input);
        }
        try {
            int exit = process.waitFor();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (exit != 0 || output.isBlank()) throw new IOException("Windows DPAPI operation failed" + (error.isBlank() ? "" : ": " + error));
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Windows DPAPI operation interrupted", exception);
        }
    }
}
