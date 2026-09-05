CREATE TABLE review_rules (
    id BIGINT NOT NULL AUTO_INCREMENT,
    scope VARCHAR(20) NOT NULL,
    project_id BIGINT NULL,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(30) NOT NULL,
    path_pattern VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    priority INT NOT NULL DEFAULT 100,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_rules_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT chk_review_rules_scope_project CHECK (
        (scope = 'GLOBAL' AND project_id IS NULL) OR (scope = 'PROJECT' AND project_id IS NOT NULL)
    )
);

CREATE INDEX idx_review_rules_global_enabled ON review_rules (scope, enabled, priority, id);
CREATE INDEX idx_review_rules_project_enabled ON review_rules (project_id, enabled, priority, id);
