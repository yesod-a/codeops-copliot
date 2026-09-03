CREATE TABLE projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    repository_path VARCHAR(768) NULL,
    last_branch VARCHAR(255) NULL,
    last_head_commit VARCHAR(40) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_projects_repository_path UNIQUE (repository_path)
);

CREATE TABLE reviews (
    id VARCHAR(36) NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    project_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    source_type VARCHAR(30) NOT NULL,
    scope VARCHAR(30) NULL,
    base_ref VARCHAR(255) NULL,
    branch VARCHAR(255) NULL,
    head_commit VARCHAR(40) NULL,
    status VARCHAR(30) NOT NULL,
    model_name VARCHAR(100) NULL,
    risk_score INT NOT NULL,
    finding_count INT NOT NULL,
    error_message TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reviews_request_id UNIQUE (request_id),
    CONSTRAINT fk_reviews_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE
);

CREATE TABLE review_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_id VARCHAR(36) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    git_status VARCHAR(30) NULL,
    additions INT NOT NULL,
    deletions INT NOT NULL,
    patch MEDIUMTEXT NOT NULL,
    content_hash VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_files_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE
);

CREATE TABLE review_findings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    review_id VARCHAR(36) NOT NULL,
    file_id BIGINT NULL,
    category VARCHAR(64) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    line_number INT NOT NULL,
    message VARCHAR(2000) NOT NULL,
    suggestion TEXT NOT NULL,
    evidence TEXT NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_review_findings_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_findings_file FOREIGN KEY (file_id) REFERENCES review_files (id) ON DELETE SET NULL
);

CREATE INDEX idx_reviews_project_created ON reviews (project_id, created_at);
CREATE INDEX idx_reviews_status_created ON reviews (status, created_at);
CREATE INDEX idx_review_files_review ON review_files (review_id);
CREATE INDEX idx_review_findings_review ON review_findings (review_id);
CREATE INDEX idx_review_findings_file ON review_findings (file_id);
