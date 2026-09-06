CREATE TABLE review_tasks (
    id VARCHAR(36) NOT NULL,
    project_id BIGINT NOT NULL,
    requested_by_user_id VARCHAR(36) NULL,
    repository_key VARCHAR(768) NOT NULL,
    title VARCHAR(500) NOT NULL,
    trigger_type VARCHAR(30) NOT NULL,
    branch VARCHAR(255) NULL,
    head_commit VARCHAR(40) NULL,
    base_ref VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL,
    outcome VARCHAR(30) NULL,
    total_groups INT NOT NULL,
    completed_groups INT NOT NULL DEFAULT 0,
    current_group INT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    error_code VARCHAR(64) NULL,
    error_message TEXT NULL,
    review_id VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_tasks_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_tasks_user FOREIGN KEY (requested_by_user_id) REFERENCES codeops_users (id) ON DELETE SET NULL,
    CONSTRAINT fk_review_tasks_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE SET NULL
);

CREATE TABLE review_task_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(36) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    git_status VARCHAR(30) NULL,
    additions INT NOT NULL,
    deletions INT NOT NULL,
    patch MEDIUMTEXT NOT NULL,
    content_hash VARCHAR(64) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_task_files_task FOREIGN KEY (task_id) REFERENCES review_tasks (id) ON DELETE CASCADE
);

CREATE TABLE review_task_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(36) NOT NULL,
    group_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    findings_json MEDIUMTEXT NULL,
    error_code VARCHAR(64) NULL,
    error_message TEXT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_review_task_groups_number UNIQUE (task_id, group_number),
    CONSTRAINT fk_review_task_groups_task FOREIGN KEY (task_id) REFERENCES review_tasks (id) ON DELETE CASCADE
);

CREATE TABLE review_task_group_files (
    group_id BIGINT NOT NULL,
    task_file_id BIGINT NOT NULL,
    PRIMARY KEY (group_id, task_file_id),
    CONSTRAINT fk_review_task_group_files_group FOREIGN KEY (group_id) REFERENCES review_task_groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_task_group_files_file FOREIGN KEY (task_file_id) REFERENCES review_task_files (id) ON DELETE CASCADE
);

CREATE TABLE review_outbox_events (
    id VARCHAR(36) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload VARCHAR(2000) NOT NULL,
    published_at DATETIME(6) NULL,
    publish_attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_review_tasks_project_created ON review_tasks (project_id, created_at);
CREATE INDEX idx_review_tasks_status_created ON review_tasks (status, created_at);
CREATE INDEX idx_review_task_files_task ON review_task_files (task_id);
CREATE INDEX idx_review_task_groups_task ON review_task_groups (task_id, group_number);
CREATE INDEX idx_review_outbox_unpublished ON review_outbox_events (published_at, created_at);
