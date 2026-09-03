package com.codeops.copilot.review.git;

import com.codeops.copilot.review.ChangedFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GitRepositoryService {
    private static final Duration COMMAND_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_FILES = 100;
    private static final int MAX_FILE_BYTES = 256 * 1024;
    private static final int MAX_PATCH_BYTES = 1024 * 1024;

    private final GitCommandRunner commandRunner;
    private final GitDiffParser diffParser;

    public GitRepositoryService(GitCommandRunner commandRunner, GitDiffParser diffParser) {
        this.commandRunner = commandRunner;
        this.diffParser = diffParser;
    }

    public RepositorySnapshot scan(Path repositoryPath, GitScope scope, String baseRef) {
        Path requestedPath = normalizeDirectory(repositoryPath);
        Path root = resolveRepositoryRoot(requestedPath);
        validateScope(scope, baseRef, root);
        String branch = runRequired(root, List.of("branch", "--show-current")).strip();
        String headCommit = runRequired(root, List.of("rev-parse", "HEAD")).strip();
        List<GitChangedFile> files = readChanges(root, scope, baseRef);
        if (files.size() > MAX_FILES) {
            throw new GitReviewException("Repository scan exceeds the maximum of " + MAX_FILES + " changed files");
        }
        int patchBytes = files.stream().mapToInt(file -> file.patch().getBytes(StandardCharsets.UTF_8).length).sum();
        if (patchBytes > MAX_PATCH_BYTES) {
            throw new GitReviewException("Repository scan exceeds the maximum patch size");
        }
        return new RepositorySnapshot(root.toString(), branch.isBlank() ? "detached HEAD" : branch, headCommit, files);
    }

    public List<ChangedFile> readSelected(Path repositoryPath, GitScope scope, String baseRef, List<String> selectedPaths) {
        if (selectedPaths == null || selectedPaths.isEmpty()) {
            throw new GitReviewException("Select at least one changed file");
        }
        Path requestedPath = normalizeDirectory(repositoryPath);
        Path root = resolveRepositoryRoot(requestedPath);
        validateScope(scope, baseRef, root);
        for (String selectedPath : selectedPaths) {
            validateRelativePath(root, selectedPath);
        }
        RepositorySnapshot snapshot = scan(root, scope, baseRef);
        Map<String, GitChangedFile> currentFiles = snapshot.files().stream()
                .collect(Collectors.toMap(GitChangedFile::path, Function.identity()));
        List<ChangedFile> selected = new ArrayList<>();
        for (String selectedPath : selectedPaths) {
            String normalizedPath = selectedPath.replace('\\', '/');
            GitChangedFile file = currentFiles.get(normalizedPath);
            if (file == null) {
                throw new GitReviewException("Selected file is no longer changed. Scan the repository again.");
            }
            if (!file.supported()) {
                throw new GitReviewException("Selected file cannot be reviewed: " + file.skipReason());
            }
            selected.add(new ChangedFile(file.path(), file.patch()));
        }
        return selected;
    }

    private List<GitChangedFile> readChanges(Path root, GitScope scope, String baseRef) {
        if (scope == GitScope.BASE_COMMIT) {
            String baseDiff = runRequired(root, List.of("diff", "--no-ext-diff", "--unified=80", baseRef + "...HEAD", "--"));
            String nameStatus = runRequired(root, List.of("diff", "--name-status", "-z", "--find-renames", baseRef + "...HEAD", "--"));
            return diffParser.parse(toPorcelain(nameStatus), baseDiff);
        }
        String status = runRequired(root, List.of("status", "--porcelain=v1", "-z", "--untracked-files=all"));
        String diff = runRequired(root, List.of("diff", "HEAD", "--no-ext-diff", "--unified=80", "--"));
        return enrichUntracked(root, diffParser.parse(status, diff));
    }

    private List<GitChangedFile> enrichUntracked(Path root, List<GitChangedFile> files) {
        List<GitChangedFile> enriched = new ArrayList<>();
        for (GitChangedFile file : files) {
            if (file.status() != GitFileStatus.UNTRACKED || !file.supported()) {
                enriched.add(file);
                continue;
            }
            Path path = root.resolve(file.path()).normalize();
            try {
                long bytes = Files.size(path);
                if (bytes > MAX_FILE_BYTES) {
                    enriched.add(new GitChangedFile(file.path(), file.status(), 0, 0, "", false, false,
                            "File exceeds the maximum review size"));
                    continue;
                }
                String content = Files.readString(path, StandardCharsets.UTF_8);
                String patch = "diff --git a/" + file.path() + " b/" + file.path() + "\n"
                        + "new file mode 100644\n--- /dev/null\n+++ b/" + file.path() + "\n"
                        + content.lines().map(line -> "+" + line).collect(Collectors.joining("\n"));
                enriched.add(new GitChangedFile(file.path(), file.status(), (int) content.lines().count(), 0,
                        patch, false, true, null));
            } catch (IOException exception) {
                enriched.add(new GitChangedFile(file.path(), file.status(), 0, 0, "", false, false,
                        "Unable to read file"));
            }
        }
        return enriched;
    }

    private String toPorcelain(String nameStatus) {
        StringBuilder porcelain = new StringBuilder();
        String[] entries = nameStatus.split("\u0000", -1);
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index];
            if (entry.isEmpty()) continue;
            String[] parts = entry.split("\t", 2);
            String code = parts[0];
            if ((code.startsWith("R") || code.startsWith("C")) && parts.length == 1 && index + 2 < entries.length) {
                String oldPath = entries[++index];
                String newPath = entries[++index];
                porcelain.append("R  ").append(newPath).append('\0').append(oldPath).append('\0');
            } else if (parts.length == 2) {
                porcelain.append(code.charAt(0)).append("  ").append(parts[1]).append('\0');
            } else if (code.length() == 1 && index + 1 < entries.length) {
                porcelain.append(code).append("  ").append(entries[++index]).append('\0');
            }
        }
        return porcelain.toString();
    }

    private Path normalizeDirectory(Path repositoryPath) {
        if (repositoryPath == null) {
            throw new GitReviewException("Repository path is required");
        }
        Path normalized = repositoryPath.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new GitReviewException("Repository path must be an existing directory");
        }
        return normalized;
    }

    private Path resolveRepositoryRoot(Path requestedPath) {
        String rootValue = runRequired(requestedPath, List.of("rev-parse", "--show-toplevel")).strip();
        if (rootValue.isBlank()) {
            throw new GitReviewException("The directory is not a Git work tree");
        }
        try {
            Path root = Path.of(rootValue).toRealPath();
            if (!requestedPath.toRealPath().startsWith(root)) {
                throw new GitReviewException("Repository path is outside the Git work tree");
            }
            return root;
        } catch (IOException exception) {
            throw new GitReviewException("Unable to resolve the Git work tree");
        }
    }

    private void validateScope(GitScope scope, String baseRef, Path root) {
        if (scope == null) {
            throw new GitReviewException("Diff scope is required");
        }
        if (scope == GitScope.BASE_COMMIT) {
            if (baseRef == null || baseRef.isBlank()) {
                throw new GitReviewException("baseRef is required for BASE_COMMIT scope");
            }
            if (baseRef.startsWith("-") || baseRef.chars().anyMatch(Character::isWhitespace)) {
                throw new GitReviewException("baseRef is invalid");
            }
            runRequired(root, List.of("rev-parse", "--verify", baseRef + "^{commit}"));
        }
    }

    private void validateRelativePath(Path root, String selectedPath) {
        if (selectedPath == null || selectedPath.isBlank()) {
            throw new GitReviewException("Selected file path is required");
        }
        Path candidate = Path.of(selectedPath.replace('\\', '/'));
        if (candidate.isAbsolute() || candidate.normalize().startsWith("..") || !root.resolve(candidate).normalize().startsWith(root)) {
            throw new GitReviewException("Selected file path must be a relative path inside the repository");
        }
    }

    private String runRequired(Path directory, List<String> arguments) {
        GitCommandResult result = commandRunner.run(directory, COMMAND_TIMEOUT, arguments);
        if (!result.successful()) {
            if (result.timedOut()) {
                throw new GitReviewException("Git command timed out");
            }
            String error = result.stderr().strip();
            throw new GitReviewException(error.isBlank() ? "Git command failed" : error);
        }
        return result.stdout();
    }
}
