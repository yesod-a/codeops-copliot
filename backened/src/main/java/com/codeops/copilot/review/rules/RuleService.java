package com.codeops.copilot.review.rules;

import com.codeops.copilot.review.persistence.ProjectJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
public class RuleService {
    private final ReviewRuleRepository ruleRepository;
    private final ProjectJpaRepository projectRepository;
    private final RuleResolutionService resolutionService;

    public RuleService(ReviewRuleRepository ruleRepository, ProjectJpaRepository projectRepository,
                       RuleResolutionService resolutionService) {
        this.ruleRepository = ruleRepository;
        this.projectRepository = projectRepository;
        this.resolutionService = resolutionService;
    }

    @Transactional(readOnly = true)
    public List<RuleView> listGlobal() {
        return ruleRepository.findByScopeOrderByPriorityAscIdAsc(RuleScope.GLOBAL).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public RulePage listGlobal(int page, int size) {
        return page(ruleRepository.findByScope(RuleScope.GLOBAL, pageable(page, size)));
    }

    @Transactional(readOnly = true)
    public List<RuleView> listProject(long projectId) {
        requireProject(projectId);
        return ruleRepository.findByScopeAndProjectIdOrderByPriorityAscIdAsc(RuleScope.PROJECT, projectId).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public RulePage listProject(long projectId, int page, int size) {
        requireProject(projectId);
        return page(ruleRepository.findByScopeAndProjectId(RuleScope.PROJECT, projectId, pageable(page, size)));
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.ASC, "priority").and(Sort.by(Sort.Direction.ASC, "id")));
    }

    private RulePage page(org.springframework.data.domain.Page<ReviewRuleEntity> result) {
        return new RulePage(result.getContent().stream().map(this::view).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public RuleView createGlobal(RuleCommand command) {
        validate(command);
        return view(ruleRepository.save(ReviewRuleEntity.global(command.name(), command.category(), command.pathPattern(),
                command.content(), command.priority(), command.enabled())));
    }

    @Transactional
    public RuleView createProject(long projectId, RuleCommand command) {
        requireProject(projectId);
        validate(command);
        return view(ruleRepository.save(ReviewRuleEntity.project(projectId, command.name(), command.category(), command.pathPattern(),
                command.content(), command.priority(), command.enabled())));
    }

    @Transactional
    public RuleView updateGlobal(long ruleId, RuleCommand command) {
        return update(ruleId, RuleScope.GLOBAL, null, command);
    }

    @Transactional
    public RuleView updateProject(long projectId, long ruleId, RuleCommand command) {
        requireProject(projectId);
        return update(ruleId, RuleScope.PROJECT, projectId, command);
    }

    @Transactional
    public void deleteGlobal(long ruleId) {
        delete(ruleId, RuleScope.GLOBAL, null);
    }

    @Transactional
    public void deleteProject(long projectId, long ruleId) {
        requireProject(projectId);
        delete(ruleId, RuleScope.PROJECT, projectId);
    }

    @Transactional(readOnly = true)
    public RuleResolutionService.RulePreview preview(long projectId, List<String> paths) {
        requireProject(projectId);
        return new RuleResolutionService.RulePreview(paths.stream().map(path -> resolutionService.resolve(projectId, path)).toList());
    }

    private RuleView update(long ruleId, RuleScope scope, Long projectId, RuleCommand command) {
        validate(command);
        ReviewRuleEntity rule = findOwned(ruleId, scope, projectId);
        rule.update(command.name(), command.category(), command.pathPattern(), command.content(), command.priority(), command.enabled());
        return view(ruleRepository.save(rule));
    }

    private void delete(long ruleId, RuleScope scope, Long projectId) {
        ruleRepository.delete(findOwned(ruleId, scope, projectId));
    }

    private ReviewRuleEntity findOwned(long ruleId, RuleScope scope, Long projectId) {
        ReviewRuleEntity rule = ruleRepository.findById(ruleId).orElseThrow(() -> new RuleNotFoundException(ruleId));
        if (rule.getScope() != scope || (projectId != null && !projectId.equals(rule.getProjectId()))) {
            throw new RuleNotFoundException(ruleId);
        }
        return rule;
    }

    private void validate(RuleCommand command) {
        if (!resolutionService.isValidGlob(command.pathPattern())) throw new RuleValidationException("路径 Glob 格式不正确");
    }

    private void requireProject(long projectId) {
        if (!projectRepository.existsById(projectId)) throw new RuleResolutionService.ProjectNotFoundException(projectId);
    }

    private RuleView view(ReviewRuleEntity rule) {
        return new RuleView(rule.getId(), rule.getScope().name(), rule.getProjectId(), rule.getName(), rule.getCategory(),
                rule.getPathPattern(), rule.getContent(), rule.getPriority(), rule.isEnabled(), rule.getVersion(),
                rule.getCreatedAt(), rule.getUpdatedAt());
    }

    public record RuleCommand(String name, String category, String pathPattern, String content, int priority, boolean enabled) {
    }

    public record RuleView(Long id, String scope, Long projectId, String name, String category, String pathPattern,
                           String content, int priority, boolean enabled, int version, LocalDateTime createdAt,
                           LocalDateTime updatedAt) {
    }

    public record RulePage(List<RuleView> items, int page, int pageSize, long total, int totalPages) {
    }

    public static class RuleNotFoundException extends RuntimeException {
        public RuleNotFoundException(long ruleId) {
            super("规则不存在: " + ruleId);
        }
    }
}
