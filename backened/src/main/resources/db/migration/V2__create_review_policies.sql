CREATE TABLE review_policies (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    pre_commit_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    pre_push_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    post_merge_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    fail_on_severity VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    fail_open BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_policies_project UNIQUE (project_id),
    CONSTRAINT fk_review_policies_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE INDEX idx_review_policies_enabled ON review_policies (enabled, pre_push_enabled);
