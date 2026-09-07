CREATE TABLE review_execution_events (
    id VARCHAR(36) NOT NULL,
    task_id VARCHAR(36) NULL,
    project_id BIGINT NULL,
    group_number INT NULL,
    event_type VARCHAR(30) NOT NULL,
    operation VARCHAR(60) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    duration_ms BIGINT NULL,
    model_name VARCHAR(120) NULL,
    input_tokens BIGINT NULL,
    output_tokens BIGINT NULL,
    total_tokens BIGINT NULL,
    estimated_cost DECIMAL(18,8) NULL,
    error_code VARCHAR(80) NULL,
    error_message VARCHAR(1000) NULL,
    metadata_json TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_execution_events_project_created ON review_execution_events (project_id, created_at);
CREATE INDEX idx_execution_events_task_created ON review_execution_events (task_id, created_at);
