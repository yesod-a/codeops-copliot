# 项目策略与 Git Hook 评审 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将项目引入、数据库评审策略、工作台项目选择和全局 Git Hook 触发评审接入现有 CodeOps Copilot。

**Architecture:** Java 在宿主机维护项目和策略 API，MySQL 保存 `projects` 与 `review_policies`；Vue 通过项目 API 选择和配置项目；全局 Hook 根据仓库根路径向 Java 查询策略，再调用 Python LangChain 评审服务并保存历史。

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, Flyway, MySQL 8, Vue 3, Vitest, PowerShell 5.1-compatible Git Hook.

**Spec:** `docs/superpowers/specs/2026-09-03-project-review-policies-design.md`

## Global Constraints

- 项目路径必须由 Java 在 Windows 宿主机校验，支持任意本机 Git 仓库。
- 项目策略和项目元数据存储在 MySQL，不使用项目内配置文件或浏览器 `localStorage`。
- LLM 评审由 Python LangChain 服务 `8090` 完成。
- Java `8080` 负责项目管理、策略解析、Git 扫描、评审持久化和历史查询。
- 未引入项目或已禁用项目不得阻断 Git 操作。

---

### Task 1: Project Policies Persistence And API

**Files:**
- Create: `backened/src/main/resources/db/migration/V2__create_review_policies.sql`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/ReviewPolicyEntity.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/ReviewPolicyRepository.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/ProjectService.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewController.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/persistence/ProjectEntity.java`
- Test: `backened/src/test/java/com/codeops/copilot/review/ProjectControllerTest.java`
- Test: `backened/src/test/java/com/codeops/copilot/review/persistence/ProjectServiceTest.java`

**Interfaces:**
- `ProjectService.importProject(String repositoryPath): ProjectView`
- `ProjectService.list(): List<ProjectView>`
- `ProjectService.get(long id): ProjectView`
- `ProjectService.updatePolicy(long id, PolicyCommand command): ProjectView`
- `ProjectService.resolvePolicy(String repositoryPath): ResolvedPolicy`
- `ProjectService.delete(long id): void`

- [ ] Add the migration and JPA policy mapping with a unique project relation and defaults.
- [ ] Add failing service/controller tests for import, list, policy update, resolve, and delete.
- [ ] Implement project root normalization using the existing `GitRepositoryService` and persist policy defaults.
- [ ] Add project endpoints and validation/error handling.
- [ ] Run `mvn -q test` from `backened`.

### Task 2: Workbench Project Selection And Project Management UI

**Files:**
- Modify: `frontend/src/api/reviewApi.js`
- Modify: `frontend/src/api/reviewApi.test.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/components/ReviewForm.vue`
- Modify: `frontend/src/components/ReviewForm.test.js`
- Modify: `frontend/src/App.test.js`
- Modify: `frontend/src/styles.css`

**Interfaces:**
- `listProjects(): Promise<ProjectView[]>`
- `importProject(repositoryPath): Promise<ProjectView>`
- `updateProjectPolicy(id, policy): Promise<ProjectView>`
- `deleteProject(id): Promise<void>`

- [ ] Add API tests for project list, import, policy update, and delete.
- [ ] Add a project selector to the workbench and emit the selected repository path.
- [ ] Add import and policy controls to the projects route.
- [ ] Keep review submission flowing through `/api/ai/review` and `/api/reviews`.
- [ ] Run `npm test -- --run` and `npm run build` from `frontend`.

### Task 3: Database-Driven Global Git Hooks

**Files:**
- Modify: `scripts/pre-push-review.ps1`
- Create: `scripts/git-review-hook.ps1`
- Modify: `scripts/install-pre-push-hook.ps1`
- Create: `scripts/git-review-hook.Tests.ps1`
- Modify: `README.md`

**Interfaces:**
- `GET /api/projects/policy/resolve?repositoryPath=...`
- `Invoke-GitReviewHook -Trigger pre-push|pre-commit|post-merge`

- [ ] Add failing tests for trigger selection, missing projects, and policy threshold behavior.
- [ ] Query Java policy before running the trigger-specific diff collection.
- [ ] Implement pre-push blocking, pre-commit staged review, and post-merge advisory review.
- [ ] Change the installer to configure one global hooks directory and install all three hook entrypoints.
- [ ] Run Pester and real fail-open/fail-closed smoke checks.

### Task 4: End-To-End Verification And Documentation

**Files:**
- Modify: `README.md`
- Modify: `backened/README.md`
- Modify: `llm-backend/README.md`

- [ ] Start MySQL, Java, LLM, and frontend services.
- [ ] Import a real local repository and update its policy through the API.
- [ ] Select the imported project in the workbench and confirm scanning uses its path.
- [ ] Execute Hook scripts with synthetic Git input and confirm policy controls behavior.
- [ ] Run Java tests, frontend tests/build, Pester, Compose validation, and `git diff --check`.
