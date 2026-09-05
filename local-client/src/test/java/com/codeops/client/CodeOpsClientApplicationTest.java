package com.codeops.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import org.junit.jupiter.api.Test;

class CodeOpsClientApplicationTest {
    @Test
    void reportsUsageForAnUnsupportedCommand() {
        var output = new ByteArrayOutputStream();

        int exit = CodeOpsClientApplication.run(new String[] {"unsupported"}, new PrintWriter(output, true));

        assertThat(exit).isEqualTo(2);
        assertThat(output.toString()).contains("Usage: codeops-client");
    }
}
