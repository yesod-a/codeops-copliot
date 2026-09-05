package com.codeops.copilot.review.rules;

import com.codeops.copilot.review.persistence.ProjectEntity;
import com.codeops.copilot.review.persistence.ProjectJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleResolutionServiceTest {
    @Test
    void resolvesGlobalThenProjectRulesWithStableHash() {
        ReviewRuleRepository rules = mock(ReviewRuleRepository.class);
        ProjectJpaRepository projects = mock(ProjectJpaRepository.class);
        ProjectEntity project = new ProjectEntity("store", "D:/store", "main", "abc");
        ReviewRuleEntity global = ReviewRuleEntity.global("事务完整性", "CORRECTNESS", "**/*.java",
                "事务必须保持原子性。", 200, true);
        ReviewRuleEntity projectRule = ReviewRuleEntity.project(8L, "订单状态流转", "QUALITY", "backend/**",
                "状态转换必须校验前置状态。", 100, true);
        when(projects.findById(8L)).thenReturn(Optional.of(project));
        when(rules.findByScopeAndEnabledTrue(RuleScope.GLOBAL)).thenReturn(List.of(global));
        when(rules.findByScopeAndProjectIdAndEnabledTrue(RuleScope.PROJECT, 8L)).thenReturn(List.of(projectRule));

        RuleResolutionService service = new RuleResolutionService(rules, projects);

        RuleResolutionService.EffectiveFileRule result = service.resolve(8L, "backend/src/OrderService.java");

        assertThat(result.rules()).extracting(RuleResolutionService.ResolvedRule::source)
                .containsExactly("GLOBAL", "PROJECT");
        assertThat(result.rules()).extracting(RuleResolutionService.ResolvedRule::name)
                .containsExactly("事务完整性", "订单状态流转");
        assertThat(result.effectiveRuleHash()).hasSize(64);
        assertThat(service.resolve(8L, "backend/src/OrderService.java").effectiveRuleHash())
                .isEqualTo(result.effectiveRuleHash());
    }

    @Test
    void rejectsAbsoluteAndEscapingPreviewPaths() {
        RuleResolutionService service = new RuleResolutionService(mock(ReviewRuleRepository.class),
                mock(ProjectJpaRepository.class));

        assertThatThrownBy(() -> service.normalizeRelativePath("D:/store/src/App.java"))
                .isInstanceOf(RuleValidationException.class);
        assertThatThrownBy(() -> service.normalizeRelativePath("../src/App.java"))
                .isInstanceOf(RuleValidationException.class);
    }

    @Test
    void usesGenericDefaultWhenNoGlobalRuleMatches() {
        ReviewRuleRepository rules = mock(ReviewRuleRepository.class);
        ProjectJpaRepository projects = mock(ProjectJpaRepository.class);
        when(projects.findById(8L)).thenReturn(Optional.of(new ProjectEntity("store", "D:/store", "main", "abc")));
        when(rules.findByScopeAndEnabledTrue(RuleScope.GLOBAL)).thenReturn(List.of());
        when(rules.findByScopeAndProjectIdAndEnabledTrue(RuleScope.PROJECT, 8L)).thenReturn(List.of());

        RuleResolutionService service = new RuleResolutionService(rules, projects);

        assertThat(service.resolve(8L, "backend/src/OrderMapper.xml").rules().getFirst().source())
                .isEqualTo("DEFAULT");
    }
}
