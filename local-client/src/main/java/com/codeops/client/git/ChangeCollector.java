package com.codeops.client.git;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChangeCollector {
    public static final int MAX_PATCH_CHARACTERS = 1_000_000;
    private static final Set<String> BINARY_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "pdf", "zip", "jar", "exe", "dll", "class", "ico");
    private final GitCommandRunner git;
    public ChangeCollector(GitCommandRunner git) { this.git = git; }

    public List<ChangedFile> collectPrePush(LocalRepository repository, PrePushUpdate update) throws IOException, InterruptedException {
        if (update.isDeletion()) return List.of();
        List<String> range = update.remoteSha().matches("0+") ? List.of(update.localSha() + "^", update.localSha()) : List.of(update.remoteSha(), update.localSha());
        return collect(repository.root(), List.of("diff", "--name-status", "--no-renames", "--no-ext-diff", range.get(0), range.get(1), "--"), range);
    }
    public List<ChangedFile> collectPreCommit(LocalRepository repository) throws IOException, InterruptedException {
        return collect(repository.root(), List.of("diff", "--cached", "--name-status", "--no-renames", "--"), List.of("--cached"));
    }
    public List<ChangedFile> collectPostMerge(LocalRepository repository) throws IOException, InterruptedException {
        return collect(repository.root(), List.of("diff", "--name-status", "--no-renames", "--no-ext-diff", "HEAD^", "HEAD", "--"), List.of("HEAD^", "HEAD"));
    }

    private List<ChangedFile> collect(Path root, List<String> statusArguments, List<String> diffRange) throws IOException, InterruptedException {
        var result = git.run(root, statusArguments);
        if (result.exitCode() != 0) throw new IOException("Could not collect Git changes");
        List<ChangedFile> files = new ArrayList<>();
        for (String line : result.output().split("\\R")) {
            int tab = line.indexOf('\t');
            if (tab < 1) continue;
            String status = line.substring(0, tab).trim();
            String path = ChangedFile.requireRelativePath(root, line.substring(tab + 1).trim());
            if (!isReviewable(path)) continue;
            List<String> patchArguments = new ArrayList<>(); patchArguments.add("diff"); patchArguments.add("--no-ext-diff"); patchArguments.add("--unified=80"); patchArguments.addAll(diffRange); patchArguments.add("--"); patchArguments.add(path);
            var patchResult = git.run(root, patchArguments);
            String patch = patchResult.output();
            if (patchResult.exitCode() != 0 || patch.isBlank() || patch.startsWith("Binary files ")) continue;
            if (patch.length() > MAX_PATCH_CHARACTERS) patch = patch.substring(0, MAX_PATCH_CHARACTERS);
            int[] stat = numstat(root, diffRange, path);
            files.add(new ChangedFile(path, status, stat[0], stat[1], patch));
        }
        return List.copyOf(files);
    }
    private int[] numstat(Path root, List<String> range, String path) throws IOException, InterruptedException {
        List<String> arguments = new ArrayList<>(); arguments.add("diff"); arguments.add("--numstat"); arguments.add("--no-renames"); arguments.addAll(range); arguments.add("--"); arguments.add(path);
        var result = git.run(root, arguments);
        if (result.exitCode() != 0 || result.output().isBlank()) return new int[] {0, 0};
        String[] columns = result.output().split("\\t", 3);
        try { return new int[] {Integer.parseInt(columns[0]), Integer.parseInt(columns[1])}; }
        catch (RuntimeException ignored) { return new int[] {0, 0}; }
    }
    private boolean isReviewable(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 || !BINARY_EXTENSIONS.contains(path.substring(dot + 1).toLowerCase());
    }
}
