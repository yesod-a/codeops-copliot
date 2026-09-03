package com.codeops.copilot.review.git;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GitDiffParser {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".kt", ".xml", ".yml", ".yaml", ".properties", ".js", ".ts", ".vue", ".json", ".md"
    );

    public List<GitChangedFile> parse(String statusPorcelain, String diffText) {
        Map<String, DiffDetails> diffs = parseDiffs(diffText == null ? "" : diffText);
        List<GitChangedFile> files = new ArrayList<>();
        String[] entries = (statusPorcelain == null ? "" : statusPorcelain).split("\u0000", -1);
        for (int index = 0; index < entries.length; index++) {
            String entry = entries[index];
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.length() < 4) {
                continue;
            }
            String code = entry.substring(0, 2);
            String path = normalizePath(entry.substring(3));
            GitFileStatus status = statusFor(code);
            if (status == GitFileStatus.RENAMED && index + 1 < entries.length) {
                index++;
            }
            DiffDetails details = diffs.getOrDefault(path, DiffDetails.empty());
            boolean binary = details.binary();
            boolean supported = !binary && isSupported(path);
            String reason = binary ? "Binary file" : supported ? null : "Unsupported file type";
            files.add(new GitChangedFile(path, status, details.additions(), details.deletions(), details.patch(),
                    binary, supported, reason));
        }
        return files;
    }

    private Map<String, DiffDetails> parseDiffs(String diffText) {
        Map<String, DiffDetails> details = new LinkedHashMap<>();
        String currentPath = null;
        StringBuilder patch = new StringBuilder();
        int additions = 0;
        int deletions = 0;
        boolean binary = false;
        for (String line : diffText.split("\\R", -1)) {
            if (line.startsWith("diff --git ")) {
                if (currentPath != null) {
                    details.put(currentPath, new DiffDetails(additions, deletions, patch.toString().stripTrailing(), binary));
                }
                currentPath = parseDiffPath(line);
                patch = new StringBuilder();
                additions = 0;
                deletions = 0;
                binary = false;
            }
            if (currentPath == null) {
                continue;
            }
            patch.append(line).append('\n');
            if (line.startsWith("Binary files ") || line.equals("GIT binary patch")) {
                binary = true;
            } else if (line.startsWith("+") && !line.startsWith("+++")) {
                additions++;
            } else if (line.startsWith("-") && !line.startsWith("---")) {
                deletions++;
            }
        }
        if (currentPath != null) {
            details.put(currentPath, new DiffDetails(additions, deletions, patch.toString().stripTrailing(), binary));
        }
        return details;
    }

    private String parseDiffPath(String header) {
        String[] paths = header.substring("diff --git ".length()).split(" ", 2);
        String target = paths.length == 2 ? paths[1] : paths[0];
        return normalizePath(target.startsWith("b/") ? target.substring(2) : target);
    }

    private GitFileStatus statusFor(String code) {
        if (code.equals("??")) return GitFileStatus.UNTRACKED;
        if (code.indexOf('R') >= 0 || code.indexOf('C') >= 0) return GitFileStatus.RENAMED;
        if (code.indexOf('A') >= 0) return GitFileStatus.ADDED;
        if (code.indexOf('D') >= 0) return GitFileStatus.DELETED;
        return GitFileStatus.MODIFIED;
    }

    private boolean isSupported(String path) {
        String lowerPath = path.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerPath::endsWith);
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/').replaceFirst("^(a|b)/", "");
    }

    private record DiffDetails(int additions, int deletions, String patch, boolean binary) {
        private static DiffDetails empty() {
            return new DiffDetails(0, 0, "", false);
        }
    }
}
