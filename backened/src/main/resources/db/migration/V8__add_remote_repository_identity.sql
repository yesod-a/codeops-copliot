ALTER TABLE projects ADD COLUMN remote_url VARCHAR(768) NULL;
ALTER TABLE projects ADD COLUMN repository_key VARCHAR(768) NULL;
ALTER TABLE projects ADD COLUMN provider VARCHAR(32) NULL;
CREATE UNIQUE INDEX uk_projects_repository_key ON projects (repository_key);
