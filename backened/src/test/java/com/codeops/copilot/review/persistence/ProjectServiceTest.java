package com.codeops.copilot.review.persistence;

import com.codeops.copilot.review.git.GitRepositoryService;
import com.codeops.copilot.review.git.GitScope;
import com.codeops.copilot.review.git.RepositoryInfo;
import com.codeops.copilot.review.git.RepositorySnapshot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectServiceTest {
    @Test
    void importsProjectWithDefaultPrePushPolicy() {
        ProjectJpaRepository projects = mock(ProjectJpaRepository.class);
        ReviewPolicyRepository policies = mock(ReviewPolicyRepository.class);
        GitRepositoryService git = mock(GitRepositoryService.class);
        when(git.inspect(Path.of("D:/repo")))
                .thenReturn(new RepositoryInfo(Path.of("D:/repo"), "main", "abc"));
        when(projects.findByRepositoryPath("D:/repo")).thenReturn(java.util.Optional.empty());
        when(projects.save(org.mockito.ArgumentMatchers.any(ProjectEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(policies.save(org.mockito.ArgumentMatchers.any(ReviewPolicyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectService service = new ProjectService(projects, policies, git);

        ProjectService.ProjectView view = service.importProject("D:/repo");

        assertThat(view.repositoryPath()).isEqualTo("D:/repo");
        assertThat(view.policy().prePushEnabled()).isTrue();
        assertThat(view.policy().preCommitEnabled()).isFalse();
        assertThat(view.policy().failOnSeverity()).isEqualTo("HIGH");
    }

    @Test
    void importsProjectWithoutScanningAllChangedFiles() {
        ProjectJpaRepository projects = mock(ProjectJpaRepository.class);
        ReviewPolicyRepository policies = mock(ReviewPolicyRepository.class);
        GitRepositoryService git = mock(GitRepositoryService.class);
        when(git.inspect(Path.of("D:/large-repo")))
                .thenReturn(new RepositoryInfo(Path.of("D:/large-repo"), "main", "abc"));
        when(projects.findByRepositoryPath("D:/large-repo")).thenReturn(java.util.Optional.empty());
        when(projects.save(org.mockito.ArgumentMatchers.any(ProjectEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(policies.save(org.mockito.ArgumentMatchers.any(ReviewPolicyEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProjectService service = new ProjectService(projects, policies, git);

        ProjectService.ProjectView view = service.importProject("D:/large-repo");

        assertThat(view.repositoryPath()).isEqualTo("D:/large-repo");
        org.mockito.Mockito.verify(git).inspect(Path.of("D:/large-repo"));
        org.mockito.Mockito.verify(git, org.mockito.Mockito.never())
                .scan(Path.of("D:/large-repo"), GitScope.WORKTREE, null);
    }

    @Test
    void resolvesMissingProjectAsDisabled() {
        ProjectJpaRepository projects = mock(ProjectJpaRepository.class);
        ReviewPolicyRepository policies = mock(ReviewPolicyRepository.class);
        GitRepositoryService git = mock(GitRepositoryService.class);
        when(git.resolveRepositoryRoot(Path.of("D:/unknown"))).thenReturn(Path.of("D:/unknown"));
        when(projects.findByRepositoryPath("D:/unknown")).thenReturn(java.util.Optional.empty());

        ProjectService service = new ProjectService(projects, policies, git);

        ProjectService.ResolvedPolicy policy = service.resolvePolicy("D:/unknown");

        assertThat(policy.enabled()).isFalse();
        assertThat(policy.projectId()).isNull();
    }
}
