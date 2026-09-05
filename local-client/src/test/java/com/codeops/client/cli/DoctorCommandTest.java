package com.codeops.client.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import org.junit.jupiter.api.Test;

class DoctorCommandTest {
    @Test
    void doctorReportsHealthyDependenciesWithoutPrintingCredentials() {
        var output = new ByteArrayOutputStream();
        var command = new DoctorCommand(StatusCommandTest.settings(), StatusCommandTest.credentials("cop_secret"),
                () -> true, () -> true, () -> true, new PrintWriter(output, true));

        assertThat(command.run()).isZero();
        assertThat(output.toString()).contains("git: available").contains("central: reachable");
        assertThat(output.toString()).doesNotContain("cop_secret");
    }
}
