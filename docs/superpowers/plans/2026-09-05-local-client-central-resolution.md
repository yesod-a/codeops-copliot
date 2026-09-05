# Local Client Central Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make centrally registered remote repositories the project identity that a future installed Local Client resolves and submits reviews against.

**Architecture:** The Spring service stores a normalized remote URL and repository key independently of the legacy local `repositoryPath`. A client authenticates with its existing Agent Token, resolves a remote URL to its authorized project, and must supply that normalized repository key again when submitting a review. Local filesystem paths never enter these endpoints.

**Tech Stack:** Spring Boot 3.5, Java 21, JPA/Flyway, MockMvc, JUnit 5.

**Spec:** `C:\Users\Administrator\.codex\attachments\680bf52c-1ab0-4c68-8a66-6620ae1bef53\pasted-text.txt`

## Global Constraints

- Central project identity is `provider + host + owner + repository`, never a client absolute path.
- Remote URL normalization must treat HTTPS and SSH forms of the same Git repository as equal.
- Client resolution and agent review require a valid Agent Token and a non-viewer project membership (unless the user is an administrator).
- Unregistered repositories resolve as disabled without exposing a project identifier.
- Legacy `repositoryPath` endpoints remain available only for local-deployment compatibility.

---

### Task 1: Persist Remote Repository Identity

**Files:**
- Create: `backened/src/main/resources/db/migration/V8__add_remote_repository_identity.sql`
- Modify: `backened/src/main/java/com/codeops/copilot/review/persistence/ProjectEntity.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/persistence/ProjectJpaRepository.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/RemoteRepositoryIdentity.java`
- Test: `backened/src/test/java/com/codeops/copilot/review/persistence/RemoteRepositoryIdentityTest.java`

**Interfaces:**
- Consumes: a Git remote URL in HTTPS, SSH shorthand, or `ssh://` form.
- Produces: `RemoteRepositoryIdentity.parse(String)` with normalized `remoteUrl`, `repositoryKey`, and `provider` fields.

- [ ] **Step 1: Write failing normalization tests**

```java
assertThat(RemoteRepositoryIdentity.parse("git@git.example.com:acme/order-service.git").repositoryKey())
        .isEqualTo("git.example.com/acme/order-service");
assertThat(RemoteRepositoryIdentity.parse("https://git.example.com/acme/order-service/").repositoryKey())
        .isEqualTo("git.example.com/acme/order-service");
```

- [ ] **Step 2: Run the focused test and observe compilation failure**

Run: `Set-Location backened; mvn test -Dtest=RemoteRepositoryIdentityTest`

- [ ] **Step 3: Add the migration, value type, entity fields, and repository lookup**

```sql
ALTER TABLE projects ADD COLUMN remote_url VARCHAR(768) NULL;
ALTER TABLE projects ADD COLUMN repository_key VARCHAR(768) NULL;
ALTER TABLE projects ADD COLUMN provider VARCHAR(32) NULL;
CREATE UNIQUE INDEX uk_projects_repository_key ON projects (repository_key);
```

- [ ] **Step 4: Re-run the focused normalization test**

Run: `Set-Location backened; mvn test -Dtest=RemoteRepositoryIdentityTest`

### Task 2: Register and Resolve Authorized Central Projects

**Files:**
- Modify: `backened/src/main/java/com/codeops/copilot/review/persistence/ProjectService.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/ProjectController.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/agent/ClientProjectController.java`
- Test: `backened/src/test/java/com/codeops/copilot/review/persistence/ProjectServiceTest.java`
- Test: `backened/src/test/java/com/codeops/copilot/review/agent/ClientProjectControllerTest.java`

**Interfaces:**
- Consumes: `POST /api/projects/register` containing `name` and `remoteUrl`; `POST /api/client/projects/resolve` containing `remoteUrl` and `Authorization: Bearer cop_...`.
- Produces: registered project identity and resolution response containing project id, repository key, role, and policy only for an authorized user.

- [ ] **Step 1: Write failing service and controller tests**

```java
mockMvc.perform(post("/api/client/projects/resolve")
        .header("Authorization", "Bearer agent-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"remoteUrl\":\"git@git.example.com:acme/order-service.git\"}"))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.projectId").value(3));
```

- [ ] **Step 2: Run focused tests and observe the missing endpoint**

Run: `Set-Location backened; mvn test -Dtest=ProjectServiceTest,ClientProjectControllerTest`

- [ ] **Step 3: Register remote identity and implement authenticated resolution**

```java
public record ClientProjectResolution(Long projectId, String projectName, String repositoryKey,
                                      ProjectMemberRole role, boolean reviewEnabled,
                                      boolean prePushEnabled, String failOnSeverity) {}
```

- [ ] **Step 4: Re-run focused controller and service tests**

Run: `Set-Location backened; mvn test -Dtest=ProjectServiceTest,ClientProjectControllerTest`

### Task 3: Bind Central Review Requests to the Resolved Remote Repository

**Files:**
- Modify: `backened/src/main/java/com/codeops/copilot/review/agent/AgentReviewController.java`
- Test: `backened/src/test/java/com/codeops/copilot/review/agent/AgentReviewControllerTest.java`

**Interfaces:**
- Consumes: an authorized `POST /api/agent/reviews` whose `repositoryKey` identifies the registered central project.
- Produces: `201 Created` only when the key matches the submitted project; returns `403` before LLM invocation otherwise.

- [ ] **Step 1: Write a failing mismatched-key authorization test**

```java
mockMvc.perform(post("/api/agent/reviews")
        .header("Authorization", "Bearer agent-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"projectId\":3,\"repositoryKey\":\"other/repository\",\"title\":\"Review\",\"files\":[{\"path\":\"src/App.java\",\"patch\":\"+class App {}\"}]}"))
    .andExpect(status().isForbidden());
```

- [ ] **Step 2: Run the focused controller test and observe the expected failure**

Run: `Set-Location backened; mvn test -Dtest=AgentReviewControllerTest`

- [ ] **Step 3: Reject key mismatches before calling the LLM service**

```java
if (!project.repositoryKey().equals(request.repositoryKey())) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Repository does not match this project");
}
```

- [ ] **Step 4: Run all backend tests**

Run: `Set-Location backened; mvn test`

### Task 4: Implement the Installed Local Client After a Supported SDK Is Available

**Files:**
- Create: `local-client/` Go module or `.NET 8` project.
- Modify: `scripts/install-pre-push-hook.ps1`
- Modify: `scripts/git-review-hook.ps1`

**Interfaces:**
- Consumes: thin Hook request on `127.0.0.1` and configured Local Client credentials.
- Produces: restricted `/v1/reviews/pre-push`, `pre-commit`, and `post-merge` endpoints which run Git only within the discovered repository root.

- [ ] **Step 1: Install Go or .NET 8 and verify its compiler is available**

Run one of: `go version` or `dotnet --version`

- [ ] **Step 2: Create client tests for repository-root validation, remote resolution, and blocked response propagation**

- [ ] **Step 3: Implement local service, Credential Manager storage, CLI install/login/status/doctor, and Windows Service registration**

- [ ] **Step 4: Convert the PowerShell Hook into a localhost-only client caller**

- [ ] **Step 5: Run client tests plus a real `git push` dry run against the local service**
