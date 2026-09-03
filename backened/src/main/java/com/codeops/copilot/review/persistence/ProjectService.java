package com.codeops.copilot.review.persistence;

import com.codeops.copilot.review.git.GitRepositoryService;
import com.codeops.copilot.review.git.GitReviewException;
import com.codeops.copilot.review.git.GitScope;
import com.codeops.copilot.review.git.RepositorySnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class ProjectService {
    private final ProjectJpaRepository projectRepository;
    private final ReviewPolicyRepository policyRepository;
    private final GitRepositoryService gitRepositoryService;

    public ProjectService(ProjectJpaRepository projectRepository, ReviewPolicyRepository policyRepository,
                          GitRepositoryService gitRepositoryService) {
        this.projectRepository = projectRepository;
        this.policyRepository = policyRepository;
        this.gitRepositoryService = gitRepositoryService;
    }

    @Transactional
    public ProjectView importProject(String repositoryPath) {
        RepositorySnapshot snapshot = gitRepositoryService.scan(Path.of(repositoryPath), GitScope.WORKTREE, null);
        String canonicalPath = canonicalPath(snapshot.repositoryPath());
        String name = projectName(canonicalPath);
        ProjectEntity project = projectRepository.findByRepositoryPath(canonicalPath)
                .orElseGet(() -> projectRepository.save(new ProjectEntity(name, canonicalPath,
                        snapshot.branch(), snapshot.headCommit())));
        project.updateMetadata(name, snapshot.branch(), snapshot.headCommit());
        ProjectEntity savedProject = projectRepository.save(project);
        ReviewPolicyEntity policy = policyFor(savedProject);
        if (policy.getId() == null || policy.getProjectId() == null) {
            policy = policyRepository.save(policy);
        }
        return toView(savedProject, policy);
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list() {
        return projectRepository.findAll().stream()
                .map(project -> toView(project, policyFor(project)))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectView get(long id) {
        ProjectEntity project = projectRepository.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
        return toView(project, policyFor(project));
    }

    @Transactional
    public ProjectView updatePolicy(long id, PolicyCommand command) {
        ProjectEntity project = projectRepository.findById(id).orElseThrow(() -> new ProjectNotFoundException(id));
        ReviewPolicyEntity policy = policyFor(project);
        policy.update(command.enabled(), command.preCommitEnabled(), command.prePushEnabled(),
                command.postMergeEnabled(), command.failOnSeverity(), command.failOpen());
        return toView(project, policyRepository.save(policy));
    }

    @Transactional(readOnly = true)
    public ResolvedPolicy resolvePolicy(String repositoryPath) {
        Path root = gitRepositoryService.resolveRepositoryRoot(Path.of(repositoryPath));
        String canonicalPath = canonicalPath(root.toString());
        return projectRepository.findByRepositoryPath(canonicalPath)
                .map(project -> resolved(project, policyFor(project)))
                .orElseGet(() -> ResolvedPolicy.disabled(canonicalPath));
    }

    @Transactional
    public void delete(long id) {
        if (!projectRepository.existsById(id)) {
            throw new ProjectNotFoundException(id);
        }
        projectRepository.deleteById(id);
    }

    private ReviewPolicyEntity policyFor(ProjectEntity project) {
        if (project.getId() == null) {
            return new ReviewPolicyEntity(project);
        }
        return policyRepository.findByProjectId(project.getId()).orElseGet(() -> new ReviewPolicyEntity(project));
    }

    private ProjectView toView(ProjectEntity project, ReviewPolicyEntity policy) {
        return new ProjectView(project.getId(), project.getName(), project.getRepositoryPath(),
                project.getLastBranch(), project.getLastHeadCommit(), policyView(policy));
    }

    private ResolvedPolicy resolved(ProjectEntity project, ReviewPolicyEntity policy) {
        return new ResolvedPolicy(project.getId(), project.getName(), project.getRepositoryPath(),
                policy.isEnabled(), policy.isPreCommitEnabled(), policy.isPrePushEnabled(),
                policy.isPostMergeEnabled(), policy.getFailOnSeverity(), policy.isFailOpen());
    }

    private PolicyView policyView(ReviewPolicyEntity policy) {
        return new PolicyView(policy.isEnabled(), policy.isPreCommitEnabled(), policy.isPrePushEnabled(),
                policy.isPostMergeEnabled(), policy.getFailOnSeverity(), policy.isFailOpen(), policy.getUpdatedAt());
    }

    private String canonicalPath(String path) {
        String normalized = path.replace('\\', '/').trim();
        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String projectName(String path) {
        Path fileName = Path.of(path).getFileName();
        return fileName == null || fileName.toString().isBlank() ? path : fileName.toString();
    }

    public record PolicyCommand(boolean enabled, boolean preCommitEnabled, boolean prePushEnabled,
                                boolean postMergeEnabled, String failOnSeverity, boolean failOpen) {
    }

    public record PolicyView(boolean enabled, boolean preCommitEnabled, boolean prePushEnabled,
                             boolean postMergeEnabled, String failOnSeverity, boolean failOpen,
                             LocalDateTime updatedAt) {
    }

    public record ProjectView(Long id, String name, String repositoryPath, String branch,
                              String headCommit, PolicyView policy) {
    }

    public record ResolvedPolicy(Long projectId, String projectName, String repositoryPath, boolean enabled,
                                 boolean preCommitEnabled, boolean prePushEnabled, boolean postMergeEnabled,
                                 String failOnSeverity, boolean failOpen) {
        static ResolvedPolicy disabled(String repositoryPath) {
            return new ResolvedPolicy(null, null, repositoryPath, false, false, false, false, "HIGH", true);
        }
    }

    public static class ProjectNotFoundException extends RuntimeException {
        public ProjectNotFoundException(long id) {
            super("Project not found: " + id);
        }
    }
}
