package com.codeops.copilot.review.git;

import com.codeops.copilot.review.ChangedFile;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitRepositoryServiceTest {
    private final GitRepositoryService service = new GitRepositoryService(
            new ProcessBuilderGitCommandRunner(), new GitDiffParser()
    );

    @Test
    void scansARealRepositoryAndRejectsPathsOutsideIt() throws Exception {
        Path repository = createGitRepository();
        Files.writeString(repository.resolve("src/App.java"), "class App { String token = \"secret\"; }\n");

        RepositorySnapshot snapshot = service.scan(repository, GitScope.WORKTREE, null);

        assertThat(snapshot.branch()).isNotBlank();
        assertThat(snapshot.headCommit()).hasSize(40);
        assertThat(snapshot.files()).extracting(GitChangedFile::path).contains("src/App.java");
        assertThatThrownBy(() -> service.readSelected(repository, GitScope.WORKTREE, null,
                List.of("../outside.txt")))
                .isInstanceOf(GitReviewException.class)
                .hasMessageContaining("relative");
    }

    @Test
    void readsOnlySelectedCurrentChanges() throws Exception {
        Path repository = createGitRepository();
        Files.writeString(repository.resolve("src/App.java"), "class App { // TODO review\n}\n");
        Files.writeString(repository.resolve("src/Other.java"), "class Other {}\n");

        List<ChangedFile> selected = service.readSelected(repository, GitScope.WORKTREE, null,
                List.of("src/App.java"));

        assertThat(selected).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("src/App.java");
            assertThat(file.content()).contains("TODO review");
        });
    }

    @Test
    void requiresBaseRefForBaseCommitScope() throws Exception {
        Path repository = createGitRepository();

        assertThatThrownBy(() -> service.scan(repository, GitScope.BASE_COMMIT, null))
                .isInstanceOf(GitReviewException.class)
                .hasMessageContaining("baseRef");
    }

    @Test
    void scansChangesSinceBaseCommit() throws Exception {
        Path repository = createGitRepository();
        Files.writeString(repository.resolve("src/App.java"), "class App { int version = 2; }\n");
        run(repository, "add", "src/App.java");
        run(repository, "commit", "-m", "Second commit");

        RepositorySnapshot snapshot = service.scan(repository, GitScope.BASE_COMMIT, "HEAD~1");

        assertThat(snapshot.files()).extracting(GitChangedFile::path).containsExactly("src/App.java");
        assertThat(snapshot.files().getFirst().additions()).isEqualTo(1);
    }

    @Test
    void includesSupportedUntrackedFilesWithReviewPatch() throws Exception {
        Path repository = createGitRepository();
        Files.writeString(repository.resolve("src/NewRule.java"), "class NewRule { // TODO test\n}\n");

        RepositorySnapshot snapshot = service.scan(repository, GitScope.WORKTREE, null);

        assertThat(snapshot.files()).anySatisfy(file -> {
            assertThat(file.path()).isEqualTo("src/NewRule.java");
            assertThat(file.status()).isEqualTo(GitFileStatus.UNTRACKED);
            assertThat(file.supported()).isTrue();
            assertThat(file.patch()).contains("TODO test");
        });
    }

    private Path createGitRepository() throws Exception {
        Path repository = Files.createTempDirectory("git-repository-");
        run(repository, "init");
        run(repository, "config", "user.email", "review@example.test");
        run(repository, "config", "user.name", "Review Test");
        Files.createDirectories(repository.resolve("src"));
        Files.writeString(repository.resolve("src/App.java"), "class App {}\n", StandardCharsets.UTF_8);
        Files.writeString(repository.resolve("src/Other.java"), "class Other {}\n", StandardCharsets.UTF_8);
        run(repository, "add", ".");
        run(repository, "commit", "-m", "Initial commit");
        return repository;
    }

    private void run(Path directory, String... arguments) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(concat("git", arguments)).directory(directory.toFile()).start();
        if (process.waitFor() != 0) {
            throw new AssertionError(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private List<String> concat(String first, String... values) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(first), java.util.Arrays.stream(values)).toList();
    }
}
