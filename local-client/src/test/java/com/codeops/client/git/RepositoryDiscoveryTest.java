package com.codeops.client.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryDiscoveryTest {
    @Test
    void resolvesGitRootAndOriginFromANestedDirectory(@TempDir Path repository) throws Exception {
        git(repository, "init", "--quiet");
        git(repository, "remote", "add", "origin", "https://git.example.com/acme/order-service.git");
        Path nested = Files.createDirectories(repository.resolve("src/main"));

        LocalRepository found = new RepositoryDiscovery(new GitCommandRunner()).discover(nested, "origin");

        assertThat(found.root()).isEqualTo(repository.toRealPath());
        assertThat(found.remoteUrl()).isEqualTo("https://git.example.com/acme/order-service.git");
    }

    private static void git(Path directory, String... arguments) throws Exception {
        Process process = new ProcessBuilder(List.of("git", "-C", directory.toString()).stream().collect(java.util.stream.Collectors.toList()))
                .command(java.util.stream.Stream.concat(java.util.stream.Stream.of("git", "-C", directory.toString()), java.util.Arrays.stream(arguments)).toList())
                .inheritIO().start();
        assertThat(process.waitFor()).isZero();
    }
}
