package com.codeops.copilot.review.rules;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReviewRuleRepository extends JpaRepository<ReviewRuleEntity, Long> {
    List<ReviewRuleEntity> findByScopeOrderByPriorityAscIdAsc(RuleScope scope);

    List<ReviewRuleEntity> findByScopeAndProjectIdOrderByPriorityAscIdAsc(RuleScope scope, Long projectId);

    List<ReviewRuleEntity> findByScopeAndEnabledTrue(RuleScope scope);

    List<ReviewRuleEntity> findByScopeAndProjectIdAndEnabledTrue(RuleScope scope, Long projectId);

    Page<ReviewRuleEntity> findByScope(RuleScope scope, Pageable pageable);

    Page<ReviewRuleEntity> findByScopeAndProjectId(RuleScope scope, Long projectId, Pageable pageable);
}
