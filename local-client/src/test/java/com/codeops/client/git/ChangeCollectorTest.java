package com.codeops.client.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ChangeCollectorTest {
    @Test
    void rejectsAChangedPathThatEscapesTheRepositoryRoot(@TempDir Path root) {
        assertThatThrownBy(() -> ChangedFile.requireRelativePath(root, "../secrets.txt"))
                .hasMessageContaining("must stay inside the repository");
    }

    @Test
    void rejectsAPrePushLineWithoutExactlyFourFields() {
        assertThatThrownBy(() -> PrePushUpdate.parse("refs/heads/main abc"))
                .hasMessageContaining("four fields");
    }
}
