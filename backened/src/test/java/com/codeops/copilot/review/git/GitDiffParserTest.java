package com.codeops.copilot.review.git;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GitDiffParserTest {
    @Test
    void parsesModifiedAddedDeletedRenamedBinaryAndUntrackedFiles() {
        String status = " M src/A.java\0A  src/New.java\0D  src/Old.java\0R  src/NewName.java\0src/OldName.java\0?? src/Notes.txt\0 M image.png\0";
        String diff = "diff --git a/src/A.java b/src/A.java\n@@ -1,2 +1,4 @@\n-old\n+new\n+added\n"
                + "diff --git a/src/New.java b/src/New.java\n@@ -0,0 +1,2 @@\n+class New {}\n"
                + "diff --git a/src/Old.java b/src/Old.java\n@@ -1 +0,0 @@\n-old\n"
                + "diff --git a/src/OldName.java b/src/NewName.java\n@@ -1 +1 @@\n-old\n+new\n"
                + "diff --git a/image.png b/image.png\nBinary files differ\n";

        List<GitChangedFile> files = new GitDiffParser().parse(status, diff);

        assertThat(files).extracting(GitChangedFile::path)
                .containsExactly("src/A.java", "src/New.java", "src/Old.java", "src/NewName.java", "src/Notes.txt", "image.png");
        assertThat(files.get(0).additions()).isEqualTo(2);
        assertThat(files.get(0).deletions()).isEqualTo(1);
        assertThat(files.get(3).status()).isEqualTo(GitFileStatus.RENAMED);
        assertThat(files.get(4).status()).isEqualTo(GitFileStatus.UNTRACKED);
        assertThat(files.get(5).binary()).isTrue();
        assertThat(files.get(5).supported()).isFalse();
    }
}
