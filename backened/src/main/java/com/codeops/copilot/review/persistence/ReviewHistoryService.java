package com.codeops.copilot.review.persistence;

import com.codeops.copilot.review.ReviewFinding;
import com.codeops.copilot.review.Severity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewHistoryService {
    private final ProjectJpaRepository projectRepository;
    private final ReviewJpaRepository reviewRepository;

    public ReviewHistoryService(ProjectJpaRepository projectRepository, ReviewJpaRepository reviewRepository) {
        this.projectRepository = projectRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public ReviewHistoryView save(SaveReviewCommand command) {
        String requestId = command.requestId().toString();
        var existing = reviewRepository.findByRequestId(requestId);
        if (existing.isPresent()) {
            return toView(existing.get());
        }

        String repository = firstNonBlank(command.repositoryPath(), command.repository());
        String projectName = projectName(repository);
        ProjectEntity project = findProject(command.repositoryPath(), projectName)
                .orElseGet(() -> projectRepository.save(new ProjectEntity(
                        projectName, blankToNull(command.repositoryPath()), command.branch(), command.headCommit())));
        project.updateMetadata(projectName, command.branch(), command.headCommit());

        List<FileCommand> fileCommands = command.files() == null ? List.of() : command.files();
        ReviewEntity review = new ReviewEntity(
                UUID.randomUUID().toString(), requestId, project, command.title(),
                defaultValue(command.sourceType(), "GIT"), command.scope(), command.baseRef(),
                command.branch(), command.headCommit(), defaultValue(command.status(), "COMPLETED"),
                command.modelName(), riskScore(command.findings()), command.errorMessage());

        Map<String, ReviewFileEntity> filesByPath = fileCommands.stream()
                .map(file -> new ReviewFileEntity(file.path(), file.gitStatus(), file.additions(), file.deletions(),
                        file.patch(), file.contentHash()))
                .peek(review::addFile)
                .collect(Collectors.toMap(ReviewFileEntity::getPath, Function.identity(), (first, ignored) -> first));

        List<FindingCommand> findingCommands = command.findings() == null ? List.of() : command.findings();
        for (FindingCommand finding : findingCommands) {
            ReviewFileEntity file = filesByPath.get(finding.file());
            review.addFinding(new ReviewFindingEntity(
                    finding.category(), finding.severity(), finding.line(), finding.message(),
                    finding.suggestion(), finding.evidence(), finding.confidence(), file));
        }
        review.setFindingCount(findingCommands.size());
        return toView(reviewRepository.saveAndFlush(review));
    }

    @Transactional(readOnly = true)
    public List<ReviewHistorySummary> list(int limit, int offset) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        int safeOffset = Math.max(0, offset);
        return reviewRepository.findAll(PageRequest.of(0, safeLimit + safeOffset,
                        Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .skip(safeOffset)
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReviewHistoryView get(UUID id) {
        return reviewRepository.findById(id.toString())
                .map(this::toView)
                .orElseThrow(() -> new ReviewNotFoundException(id));
    }

    @Transactional
    public void delete(UUID id) {
        if (!reviewRepository.existsById(id.toString())) {
            throw new ReviewNotFoundException(id);
        }
        reviewRepository.deleteById(id.toString());
    }

    private java.util.Optional<ProjectEntity> findProject(String repositoryPath, String projectName) {
        if (repositoryPath != null && !repositoryPath.isBlank()) {
            return projectRepository.findByRepositoryPath(repositoryPath);
        }
        return projectRepository.findFirstByNameAndRepositoryPathIsNull(projectName);
    }

    private ReviewHistorySummary toSummary(ReviewEntity review) {
        return new ReviewHistorySummary(
                UUID.fromString(review.getId()), review.getTitle(), repositoryValue(review.getProject()),
                review.getSourceType(), review.getStatus(), review.getRiskScore(), review.getFindingCount(),
                review.getCreatedAt(), review.getCompletedAt());
    }

    private ReviewHistoryView toView(ReviewEntity review) {
        List<ReviewFileView> files = review.getFiles().stream()
                .map(file -> new ReviewFileView(file.getPath(), file.getGitStatus(), file.getAdditions(),
                        file.getDeletions(), file.getPatch(), file.getContentHash()))
                .toList();
        List<ReviewFinding> findings = review.getFindings().stream()
                .map(finding -> new ReviewFinding(finding.getCategory(), finding.getSeverity(), finding.getFilePath(),
                        finding.getLineNumber(), finding.getMessage(), finding.getSuggestion(), finding.getEvidence(),
                        finding.getConfidence()))
                .toList();
        return new ReviewHistoryView(
                UUID.fromString(review.getId()), repositoryValue(review.getProject()), review.getTitle(),
                review.getSourceType(), review.getScope(), review.getBaseRef(), review.getBranch(),
                review.getHeadCommit(), review.getStatus(), review.getModelName(), review.getRiskScore(),
                review.getFindingCount(), review.getErrorMessage(), review.getCreatedAt(), review.getCompletedAt(),
                files, findings);
    }

    private int riskScore(List<FindingCommand> findings) {
        int penalty = findings == null ? 0 : findings.stream().mapToInt(finding -> switch (finding.severity()) {
            case CRITICAL -> 26;
            case HIGH -> 15;
            case MEDIUM -> 8;
            case LOW -> 3;
        }).sum();
        return Math.max(0, Math.min(100, 100 - penalty));
    }

    private String repositoryValue(ProjectEntity project) {
        return project.getRepositoryPath() == null ? project.getName() : project.getRepositoryPath();
    }

    private String projectName(String repository) {
        if (repository == null || repository.isBlank()) {
            return "未命名项目";
        }
        String normalized = repository.replace('\\', '/');
        if (!normalized.matches("^[A-Za-z]:/.*") && !normalized.startsWith("/")) {
            return normalized;
        }
        String name = Path.of(normalized).getFileName() == null ? normalized : Path.of(normalized).getFileName().toString();
        return name.isBlank() ? normalized : name;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record SaveReviewCommand(
            UUID requestId,
            String repositoryPath,
            String repository,
            String title,
            String sourceType,
            String scope,
            String baseRef,
            String branch,
            String headCommit,
            String modelName,
            List<FileCommand> files,
            List<FindingCommand> findings,
            String status,
            String errorMessage
    ) {
        public SaveReviewCommand(UUID requestId, String repositoryPath, String repository, String title,
                                 String sourceType, String scope, String baseRef, String branch,
                                 String headCommit, String modelName, List<FileCommand> files,
                                 List<FindingCommand> findings) {
            this(requestId, repositoryPath, repository, title, sourceType, scope, baseRef, branch, headCommit,
                    modelName, files, findings, "COMPLETED", null);
        }
    }

    public record FileCommand(String path, String gitStatus, int additions, int deletions,
                              String patch, String contentHash) {
    }

    public record FindingCommand(String file, String category, Severity severity, int line,
                                 String message, String suggestion, String evidence, double confidence) {
    }

    public record ReviewHistorySummary(UUID id, String title, String repository, String sourceType,
                                       String status, int riskScore, int findingCount,
                                       LocalDateTime createdAt, LocalDateTime completedAt) {
    }

    public record ReviewHistoryView(UUID id, String repository, String title, String sourceType,
                                    String scope, String baseRef, String branch, String headCommit,
                                    String status, String modelName, int riskScore, int findingCount,
                                    String errorMessage, LocalDateTime createdAt, LocalDateTime completedAt,
                                    List<ReviewFileView> files, List<ReviewFinding> findings) {
    }

    public record ReviewFileView(String path, String gitStatus, int additions, int deletions,
                                 String patch, String contentHash) {
    }

    public static class ReviewNotFoundException extends RuntimeException {
        public ReviewNotFoundException(UUID id) {
            super("Review not found: " + id);
        }
    }
}
