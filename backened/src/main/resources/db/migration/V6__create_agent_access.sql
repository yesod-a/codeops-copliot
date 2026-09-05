CREATE TABLE codeops_users (
    id VARCHAR(36) NOT NULL,
    username VARCHAR(120) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_codeops_users_username UNIQUE (username)
);

CREATE TABLE codeops_agents (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    name VARCHAR(160) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_codeops_agents_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_codeops_agents_user FOREIGN KEY (user_id) REFERENCES codeops_users (id) ON DELETE CASCADE
);

CREATE TABLE project_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES codeops_users (id) ON DELETE CASCADE
);

CREATE INDEX idx_codeops_agents_user ON codeops_agents (user_id, active);
CREATE INDEX idx_project_members_user ON project_members (user_id, project_id);
