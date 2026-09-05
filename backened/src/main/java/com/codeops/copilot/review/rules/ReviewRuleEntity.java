package com.codeops.copilot.review.rules;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "review_rules")
public class ReviewRuleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleScope scope;

    @Column(name = "project_id")
    private Long projectId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "path_pattern", nullable = false, length = 255)
    private String pathPattern;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private int priority;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ReviewRuleEntity() {
    }

    private ReviewRuleEntity(RuleScope scope, Long projectId, String name, String category, String pathPattern,
                             String content, int priority, boolean enabled) {
        this.scope = scope;
        this.projectId = projectId;
        this.name = name;
        this.category = category;
        this.pathPattern = pathPattern;
        this.content = content;
        this.priority = priority;
        this.enabled = enabled;
    }

    public static ReviewRuleEntity global(String name, String category, String pathPattern, String content,
                                          int priority, boolean enabled) {
        return new ReviewRuleEntity(RuleScope.GLOBAL, null, name, category, pathPattern, content, priority, enabled);
    }

    public static ReviewRuleEntity project(long projectId, String name, String category, String pathPattern,
                                           String content, int priority, boolean enabled) {
        return new ReviewRuleEntity(RuleScope.PROJECT, projectId, name, category, pathPattern, content, priority, enabled);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void update(String name, String category, String pathPattern, String content, int priority, boolean enabled) {
        this.name = name;
        this.category = category;
        this.pathPattern = pathPattern;
        this.content = content;
        this.priority = priority;
        this.enabled = enabled;
        version++;
    }

    public Long getId() { return id; }
    public RuleScope getScope() { return scope; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getPathPattern() { return pathPattern; }
    public String getContent() { return content; }
    public int getPriority() { return priority; }
    public boolean isEnabled() { return enabled; }
    public int getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
