# 评审历史数据库持久化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 MySQL 持久化项目、评审、变更文件和 LLM 问题，并让前端历史记录完全通过 Java API 读取，不再使用 `localStorage`。

**Architecture:** 前端直接调用 Python LangChain 服务完成 Git 和手动代码评审；评审成功后将结构化结果提交 Java 持久化 API。Java 只负责本机 Git 扫描、MySQL 数据访问和历史查询，Flyway 创建数据库结构。

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Data JPA, MySQL 8, Flyway, H2 test database, Vue 3, Vitest, Docker Compose。

**Spec:** `docs/superpowers/specs/2026-09-03-review-history-database-design.md`

## Global Constraints

- 不把大模型调用重新放回 Java；Java 不保留评审推理职责。
- 前端不使用 `localStorage` 保存或读取评审历史。
- Git 扫描仍由 Java 在宿主机执行，因此支持本机任意 Git 仓库路径。
- 评审补丁保存到数据库，完整源文件不单独保存。
- 不回滚工作区中已有的用户改动或历史改动。

---

### Task 1: Database Schema And Java Persistence

**Files:**
- Modify: `backened/pom.xml`
- Modify: `backened/src/main/resources/application.yml`
- Create: `backened/src/main/resources/db/migration/V1__create_review_history.sql`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/ProjectEntity.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/ReviewEntity.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/ReviewFileEntity.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/ReviewFindingEntity.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/ProjectJpaRepository.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/ReviewJpaRepository.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/persistence/ReviewHistoryService.java`
- Test: `backened/src/test/java/com/codeops/copilot/review/persistence/ReviewHistoryServiceTest.java`
- Create: `backened/src/test/resources/application-test.yml`

**Interfaces:**
- `ReviewHistoryService.save(SaveReviewCommand command): ReviewHistoryView`
- `ReviewHistoryService.list(int limit, int offset): List<ReviewHistorySummary>`
- `ReviewHistoryService.get(UUID id): ReviewHistoryView`
- `ReviewHistoryService.delete(UUID id): void`

- [ ] **Step 1: Add persistence dependencies and a failing service test**

Add Spring Data JPA, MySQL runtime, Flyway MySQL support, and H2 test dependencies. Write a `@DataJpaTest` covering save, reload, and cascade deletion of one review with files and findings.

- [ ] **Step 2: Run the persistence test and confirm the expected missing implementation failure**

Run `mvn -q -Dtest=ReviewHistoryServiceTest test` from `backened`. It should fail because the persistence entities and service do not exist yet.

- [ ] **Step 3: Add the Flyway migration**

Create `projects`, `reviews`, `review_files`, and `review_findings` with UUID review IDs, unique `request_id`, foreign keys, cascading deletes, and indexes for project/date and status/date history queries.

- [ ] **Step 4: Implement entities and repositories**

Map the four tables with JPA. Use `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)` from review to files and findings. Use `@EntityGraph` or an explicit fetch query for review details so API mapping never serializes lazy entities directly.

- [ ] **Step 5: Implement the transactional history service**

Upsert a project by repository path when present, map all submitted files and findings, calculate risk score from severities, enforce `request_id` idempotency, and return API view objects. For manual reviews with no local path, use the repository display string as the project name and leave `repository_path` null.

- [ ] **Step 6: Run the persistence test and the full Java test suite**

Run `mvn -q -Dtest=ReviewHistoryServiceTest test` and then `mvn test` from `backened`. Expected result: all tests pass.

---

### Task 2: Replace Java Review Tasks With Persistence API

**Files:**
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewController.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewConfiguration.java`
- Modify: `backened/src/main/java/com/codeops/copilot/CodeOpsCopilotApplication.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/ai/AiBackendProperties.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/ai/AiReviewClient.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/ai/HttpAiReviewClient.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/ReviewService.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/ReviewTask.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/ReviewTaskRepository.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/InMemoryReviewTaskRepository.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/ReviewRequest.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/ReviewRule.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/SensitiveDataRule.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/TodoCommentRule.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/ReviewStatus.java`
- Delete: `backened/src/test/java/com/codeops/copilot/review/ReviewServiceTest.java`
- Delete: `backened/src/test/java/com/codeops/copilot/review/ReviewConfigurationTest.java`
- Delete: `backened/src/test/java/com/codeops/copilot/review/ai/HttpAiReviewClientTest.java`
- Modify: `backened/src/test/java/com/codeops/copilot/review/ReviewControllerTest.java`

**Interfaces:**
- `POST /api/reviews` saves a completed LLM result and returns its UUID.
- `GET /api/reviews?limit=20&offset=0` returns history summaries.
- `GET /api/reviews/{id}` returns full details.
- `DELETE /api/reviews/{id}` deletes a review and cascades its files and findings.
- `POST /api/repositories/scan` remains unchanged.

- [ ] **Step 1: Write controller tests for save, list, detail, and delete**

Use `@WebMvcTest` with a mocked `ReviewHistoryService`. Assert request validation, JSON response shape, and that the controller does not depend on an AI client or asynchronous review task.

- [ ] **Step 2: Run controller tests and confirm they fail against the old task API**

Run `mvn -q -Dtest=ReviewControllerTest test`. The new request and service contracts should fail before the controller refactor.

- [ ] **Step 3: Replace controller request and response records**

Add records for `SaveReviewRequest`, `ReviewFileRequest`, `ReviewFindingRequest`, `ReviewHistorySummary`, and `ReviewHistoryResponse`. Keep Git scan request and error handling intact.

- [ ] **Step 4: Wire the controller to `ReviewHistoryService`**

Make `POST /api/reviews` synchronous and persistence-only. Remove `/api/reviews/{id}/process` and `/api/reviews/from-git` from the primary API because the frontend no longer submits reviews through Java.

- [ ] **Step 5: Remove obsolete Java AI and in-memory task code**

Delete the old async task, deterministic review rule, and HTTP AI client classes and remove `@EnableAsync` and AI beans from configuration.

- [ ] **Step 6: Run all Java tests**

Run `mvn test` from `backened`. Expected result: Git scanner tests, persistence tests, and controller tests pass.

---

### Task 3: Persist LLM Results And Load History In Vue

**Files:**
- Modify: `frontend/src/api/reviewApi.js`
- Modify: `frontend/src/api/reviewApi.test.js`
- Modify: `frontend/src/components/ReviewForm.vue`
- Modify: `frontend/src/components/ReviewForm.test.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/navigation.js`
- Modify: `frontend/src/components/ReviewStatus.vue`
- Create or modify: `frontend/src/App.test.js`

**Interfaces:**
- `submitAiReview(payload): Promise<{ findings: ReviewFinding[] }>`
- `saveReview(payload): Promise<ReviewHistoryResponse>`
- `listReviews(params): Promise<ReviewHistorySummary[]>`
- `getReviewDetails(id): Promise<ReviewHistoryResponse>`
- `deleteReview(id): Promise<void>`

- [ ] **Step 1: Add failing API and component tests**

Assert that manual and Git review submissions call `/api/ai/review`, successful findings are sent to `/api/reviews`, history uses `GET /api/reviews`, and no test setup depends on `localStorage`.

- [ ] **Step 2: Run frontend tests and confirm the old localStorage behavior fails the new expectations**

Run `npm test -- --run` from `frontend`. The new API expectations should fail before the implementation changes.

- [ ] **Step 3: Implement persistence API helpers and payload mapping**

Add save/list/detail/delete helpers. Include Git file status, additions, deletions, and patch content in the save payload. Give each save a client request UUID.

- [ ] **Step 4: Change `App.vue` to use the LLM-then-save flow**

After either review mode receives LLM findings, save the completed result through Java, render the returned persisted record, and show a clear notice when LLM succeeds but database saving fails. On mount and on the history route, load summaries from Java.

- [ ] **Step 5: Remove localStorage history code and add history detail interaction**

Delete `loadReviewHistory`, `saveReviewHistory`, and `rememberReview` localStorage writes. Make history entries load their full record from the API and display the selected report in the workbench.

- [ ] **Step 6: Run the full frontend test suite and production build**

Run `npm test -- --run` and `npm run build` from `frontend`. Expected result: all tests pass and Vite builds successfully.

---

### Task 4: Add MySQL To Local Docker Workflow

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `scripts/start-local-stack.ps1`
- Modify: `README.md`
- Modify: `backened/README.md`

- [ ] **Step 1: Add MySQL service and Java datasource defaults**

Add a MySQL 8 service with a named volume, healthcheck, database/user/password variables, and port `3306`. Configure the host Java process through environment variables exported by the startup script.

- [ ] **Step 2: Validate the Compose configuration**

Run `docker compose config` and confirm the MySQL service, healthcheck, and volume are present.

- [ ] **Step 3: Update the local startup script and documentation**

Start MySQL before Java, set `SPRING_DATASOURCE_*` variables, document database credentials and the fact that Java is still host-local for arbitrary Windows repository access.

- [ ] **Step 4: Rebuild and start the stack**

Run `docker compose up -d --build mysql llm-backend frontend` and verify MySQL is healthy, Python is healthy, and frontend is reachable.

- [ ] **Step 5: Verify end-to-end persistence across restart**

Submit a real LLM review through `http://127.0.0.1:5173`, query `GET /api/reviews`, restart Java and the frontend, query again, and confirm the same review and findings remain available.

---

## Final Verification

- [ ] `mvn test` passes.
- [ ] `npm test -- --run` passes.
- [ ] `npm run build` passes.
- [ ] `docker compose config` passes.
- [ ] MySQL, LLM backend, and frontend containers are healthy/running.
- [ ] `rg "localStorage" frontend/src` returns no history implementation.
- [ ] Nginx logs show LLM requests at `/api/ai/review` and Java requests only for scan/save/history APIs.
