# Central Docker Service and Local Git Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the complete CodeOps server stack in Docker and configure each developer repository to submit local Git patches through the Hook client.

**Architecture:** Docker Compose runs MySQL, the LLM service, the Java central API, and the static frontend. The frontend routes API calls across the Compose network. The local PowerShell client reads only local Git data and repository-local client configuration, then submits authenticated patch reviews to the central API.

**Tech Stack:** Docker Compose, MySQL 8.4, Spring Boot 3.5 / Java 21, Python FastAPI / LangChain, Vue / Nginx, PowerShell, Pester.

**Spec:** `docs/superpowers/specs/2026-09-04-central-docker-client-design.md`

## Global Constraints

- The central API must not scan or persist a local client absolute path.
- Client configuration is `.codeops/client.json` and must be UTF-8 without BOM.
- Environment variables override repository configuration for all existing `CODEOPS_*` settings.
- Git push remains fail-closed unless `failOpen` is explicitly true.
- Production Compose configuration must require a non-default `CODEOPS_BOOTSTRAP_TOKEN`.

---

### Task 1: Containerize the Java Central API

**Files:**
- Create: `backened/Dockerfile`
- Modify: `docker-compose.yml`
- Modify: `frontend/nginx.conf`
- Modify: `.env.example`
- Test: Compose configuration inspection

**Interfaces:**
- Consumes: `SPRING_DATASOURCE_*`, `CODEOPS_AI_URL`, and `CODEOPS_BOOTSTRAP_TOKEN` environment variables.
- Produces: `backend:8080` for Nginx and `http://llm-backend:8090/api/ai/review` as the central LLM upstream.

- [ ] **Step 1: Add the Compose-level expected service test command**

Run:

```powershell
docker compose config
```

Expected before implementation: output has no `backend:` service and frontend `/api/` uses `host.docker.internal`.

- [ ] **Step 2: Add a multi-stage Java Dockerfile**

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

- [ ] **Step 3: Add `backend` to Compose and change Nginx upstream**

Set backend datasource host to `mysql`, set `CODEOPS_AI_URL` to `http://llm-backend:8090/api/ai/review`, and change Nginx `location /api/` to `proxy_pass http://backend:8080;`.

- [ ] **Step 4: Re-run Compose inspection**

Run:

```powershell
docker compose config
```

Expected: rendered services include `backend`; no frontend API proxy points at `host.docker.internal`.

### Task 2: Provide Repository-local Client Configuration

**Files:**
- Create: `scripts/initialize-codeops-client.ps1`
- Create: `scripts/uninstall-pre-push-hook.ps1`
- Modify: `scripts/git-review-hook.ps1`
- Modify: `scripts/install-pre-push-hook.ps1`
- Modify: `scripts/git-review-hook.Tests.ps1`

**Interfaces:**
- Consumes: `.codeops/client.json` with `serverUrl`, `projectId`, `agentToken`, `timeoutSeconds`, and `failOpen` properties.
- Produces: `Get-CodeOpsClientConfiguration -RepositoryRoot <path>` and resolved central configuration held in `$script:CodeOpsServerUrl`, `$script:CodeOpsAgentToken`, `$script:CodeOpsProjectId`, `$script:CodeOpsTimeoutSeconds`.

- [ ] **Step 1: Add a failing Pester test for file configuration**

```powershell
It 'loads central configuration from the repository client file' {
    $config = Get-CodeOpsClientConfiguration -RepositoryRoot $TestDrive
    $config.ServerUrl | Should Be 'https://codeops.example.com'
    $config.ProjectId | Should Be 3
}
```

Expected before implementation: failure because `Get-CodeOpsClientConfiguration` is undefined.

- [ ] **Step 2: Implement configuration loading and environment precedence**

Read `.codeops/client.json` using UTF-8, validate non-empty endpoint and token with a positive project id, and overwrite only configuration values supplied through non-empty environment variables.

- [ ] **Step 3: Apply the resolved configuration before central-mode detection**

Inside the Hook execution path, resolve the Git root before detecting central mode, populate the script-level values, and then choose `/api/agent/reviews` when all central fields are valid.

- [ ] **Step 4: Add initialization and uninstall scripts**

Implement initialization parameters `ServerUrl`, `ProjectId`, `AgentToken`, `TimeoutSeconds`, and `FailOpen`. Create `.codeops/client.json` atomically as UTF-8 without BOM and append `.codeops/client.json` to `.git/info/exclude`. Uninstall CodeOps global hooks only after proving `core.hooksPath` equals its managed directory.

- [ ] **Step 5: Run Pester tests**

Run:

```powershell
Invoke-Pester .\scripts\git-review-hook.Tests.ps1
```

Expected: all Hook policy, legacy-path, configuration, and precedence tests pass.

### Task 3: Make Central Mode the Deployable Documentation Path

**Files:**
- Modify: `README.md`
- Modify: `backened/README.md`
- Modify: `.env.example`
- Modify: `scripts/start-local-stack.ps1`

**Interfaces:**
- Consumes: the Compose and Hook client interfaces created by Tasks 1 and 2.
- Produces: commands for server deployment, first administrator bootstrap, Agent Token generation, per-repository client setup, and local development startup.

- [ ] **Step 1: Replace host Java startup instructions with Compose startup**

```powershell
Copy-Item .env.example .env
# Set AI_API_KEY and a random CODEOPS_BOOTSTRAP_TOKEN.
docker compose up -d --build
```

- [ ] **Step 2: Document client installation and per-repository setup**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File <codeops-root>\scripts\install-pre-push-hook.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File <codeops-root>\scripts\initialize-codeops-client.ps1 `
  -ServerUrl https://codeops.example.com -ProjectId 3 -AgentToken cop_...
```

- [ ] **Step 3: Adjust local convenience script**

Make `start-local-stack.ps1` call `docker compose up --build` for all services and remove the host Maven process launch.

- [ ] **Step 4: Validate documentation commands against the repository**

Run:

```powershell
docker compose config
Get-Help .\scripts\initialize-codeops-client.ps1 -Full
```

Expected: Compose renders without unresolved required values when `.env` is configured; initialization script exposes all documented parameters.

### Task 4: Verify Server and Client Regression Coverage

**Files:**
- Modify: `backened/src/test/java/com/codeops/copilot/review/agent/AgentReviewControllerTest.java`
- Test: `backened/src/test/java/com/codeops/copilot/review/agent/AgentReviewControllerTest.java`
- Test: `scripts/git-review-hook.Tests.ps1`

**Interfaces:**
- Consumes: central request `POST /api/agent/reviews` with `Authorization: Bearer cop_...`.
- Produces: HTTP 401 without Agent Token and HTTP 201 only when a token is authorized for its project.

- [ ] **Step 1: Add a failing controller test for path-free central payloads**

```java
mockMvc.perform(post("/api/agent/reviews")
        .header("Authorization", "Bearer agent-token")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"projectId\":3,\"title\":\"Review\",\"files\":[{\"path\":\"src/App.java\",\"patch\":\"+class App {}\"}]}"))
    .andExpect(status().isCreated());
```

Expected: the existing central test remains valid and has no `repositoryPath` field.

- [ ] **Step 2: Run focused backend and client tests**

Run:

```powershell
Set-Location backened; mvn test -Dtest=AgentReviewControllerTest
Set-Location ..; Invoke-Pester .\scripts\git-review-hook.Tests.ps1
```

Expected: both commands pass.

- [ ] **Step 3: Build images and inspect live health endpoints**

Run:

```powershell
docker compose up -d --build
Invoke-WebRequest http://127.0.0.1:5173/api/ai/health
```

Expected: containers are healthy and Nginx can reach the LLM health endpoint through the Compose network.
