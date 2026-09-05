package com.codeops.copilot.review.rules;

import com.codeops.copilot.review.persistence.ProjectJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class RuleResolutionService {
    private static final String DEFAULT_RULE = "只报告变更中有直接证据的正确性、安全性或可靠性问题，不要臆测未提供的上下文。";

    private final ReviewRuleRepository ruleRepository;
    private final ProjectJpaRepository projectRepository;

    public RuleResolutionService(ReviewRuleRepository ruleRepository, ProjectJpaRepository projectRepository) {
        this.ruleRepository = ruleRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public EffectiveFileRule resolve(long projectId, String path) {
        projectRepository.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
        String normalizedPath = normalizeRelativePath(path);
        List<ResolvedRule> resolved = new ArrayList<>();
        appendMatching(resolved, ruleRepository.findByScopeAndEnabledTrue(RuleScope.GLOBAL), normalizedPath, "GLOBAL");
        appendMatching(resolved, ruleRepository.findByScopeAndProjectIdAndEnabledTrue(RuleScope.PROJECT, projectId), normalizedPath, "PROJECT");
        if (resolved.isEmpty()) {
            resolved.add(new ResolvedRule("DEFAULT", null, "通用默认规则", "**", "default-1", DEFAULT_RULE));
        }
        return new EffectiveFileRule(normalizedPath, hash(resolved), List.copyOf(resolved));
    }

    public String normalizeRelativePath(String path) {
        if (path == null || path.isBlank()) throw new RuleValidationException("文件路径不能为空");
        String normalized = path.trim().replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:/.*")) {
            throw new RuleValidationException("文件路径必须相对于仓库根目录");
        }
        for (String segment : normalized.split("/")) {
            if (segment.equals("..") || segment.isBlank()) throw new RuleValidationException("文件路径不能包含路径逃逸");
        }
        return normalized;
    }

    public boolean isValidGlob(String glob) {
        if (glob == null || glob.isBlank() || glob.length() > 255 || glob.contains("..") || glob.startsWith("/")) return false;
        try {
            globPattern(glob);
            return true;
        } catch (RuleValidationException exception) {
            return false;
        }
    }

    private void appendMatching(List<ResolvedRule> destination, List<ReviewRuleEntity> rules, String path, String source) {
        rules.stream()
                .sorted(Comparator.comparingInt(ReviewRuleEntity::getPriority)
                        .thenComparing(rule -> rule.getId() == null ? Long.MIN_VALUE : rule.getId()))
                .filter(rule -> matches(rule.getPathPattern(), path))
                .forEach(rule -> destination.add(new ResolvedRule(source, rule.getId(), rule.getName(), rule.getPathPattern(),
                        String.valueOf(rule.getVersion()), rule.getContent())));
    }

    private boolean matches(String glob, String path) {
        return globPattern(glob).matcher(path).matches();
    }

    private Pattern globPattern(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char character = glob.charAt(index);
            if (character == '*') {
                boolean doubleStar = index + 1 < glob.length() && glob.charAt(index + 1) == '*';
                if (doubleStar) {
                    index++;
                    if (index + 1 < glob.length() && glob.charAt(index + 1) == '/') {
                        index++;
                        regex.append("(?:.*/)?");
                    } else {
                        regex.append(".*");
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else if (character == '?') {
                regex.append("[^/]");
            } else if (character == '{') {
                int close = glob.indexOf('}', index);
                if (close < 0) throw new RuleValidationException("Glob 花括号没有闭合");
                String alternatives = glob.substring(index + 1, close);
                if (alternatives.isBlank()) throw new RuleValidationException("Glob 花括号不能为空");
                regex.append("(?:");
                String[] values = alternatives.split(",", -1);
                for (int item = 0; item < values.length; item++) {
                    if (values[item].isBlank()) throw new RuleValidationException("Glob 花括号包含空选项");
                    if (item > 0) regex.append('|');
                    regex.append(Pattern.quote(values[item]));
                }
                regex.append(')');
                index = close;
            } else {
                if ("\\.^$|+()[]".indexOf(character) >= 0) regex.append('\\');
                regex.append(character);
            }
        }
        return Pattern.compile(regex.append('$').toString(), Pattern.CASE_INSENSITIVE);
    }

    private String hash(List<ResolvedRule> rules) {
        String payload = rules.stream().map(rule -> String.join("\u001f", rule.source(), String.valueOf(rule.id()),
                rule.name(), rule.pattern(), rule.version(), rule.content())).reduce("", (left, right) -> left + "\u001e" + right);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public record ResolvedRule(String source, Long id, String name, String pattern, String version, String content) {
    }

    public record EffectiveFileRule(String path, String effectiveRuleHash, List<ResolvedRule> rules) {
    }

    public record RulePreview(List<EffectiveFileRule> files) {
    }

    public static class ProjectNotFoundException extends RuntimeException {
        public ProjectNotFoundException(long projectId) {
            super("项目不存在: " + projectId);
        }
    }
}
