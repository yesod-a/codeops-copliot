package com.codeops.copilot.review.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RemoteRepositoryIdentityTest {
    @Test
    void normalizesHttpsAndSshUrlsToTheSameRepositoryKey() {
        RemoteRepositoryIdentity https = RemoteRepositoryIdentity.parse(
                "https://git.example.com/acme/order-service/");
        RemoteRepositoryIdentity ssh = RemoteRepositoryIdentity.parse(
                "git@git.example.com:acme/order-service.git");
        RemoteRepositoryIdentity sshUri = RemoteRepositoryIdentity.parse(
                "ssh://git@git.example.com/acme/order-service.git");

        assertThat(https.repositoryKey()).isEqualTo("git.example.com/acme/order-service");
        assertThat(ssh.repositoryKey()).isEqualTo(https.repositoryKey());
        assertThat(sshUri.repositoryKey()).isEqualTo(https.repositoryKey());
        assertThat(https.provider()).isEqualTo("GIT");
    }
}
