# CodeOps 可观测性 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为异步评审链路增加任务、分组、LLM、工具调用和队列的可观测数据，并在前端提供项目级监控和任务执行时间线。

**Architecture:** MySQL 保存不可变的执行事件，Micrometer 暴露低基数实时指标。Python LLM 服务返回本次评审的调用统计，Java Worker 将其与任务、分组事件关联后持久化；前端通过权限过滤后的聚合 API 查询，不直接读取 Prometheus 或数据库。

**Tech Stack:** Spring Boot 3.5、Spring Data JPA、Flyway、Spring AMQP、Micrometer/Actuator、MySQL、Python 3.10、FastAPI、LangChain、Vue 3、Docker Compose。

**Spec:** `docs/superpowers/specs/2026-09-06-observability-design.md`

## Global Constraints

- 不保存完整 Prompt、完整 diff、工具返回代码、Token 或密码等敏感值。
- 指标标签不得包含 taskId、groupId、文件路径或用户输入，只使用有限枚举和模型名。
- 观测事件写入失败不得改变评审任务状态。
- 管理员可查看全部项目，普通用户只能查看具备评审权限的项目。
- 默认 Docker Compose 不启动 Prometheus/Grafana，Prometheus 使用可选 `observability` profile。
- 保持现有异步评审、RabbitMQ Outbox、重试和取消行为不变。

---

### Task 1: 依赖、配置和数据库事件表

**Files:**
- Modify: `backened/pom.xml`
- Modify: `backened/src/main/resources/application.yml`
- Create: `backened/src/main/resources/db/migration/V10__create_review_execution_events.sql`
- Test: `backened/src/test/java/com/codeops/copilot/review/ObservabilitySchemaTest.java`

**Interfaces:**
- Produces the `review_execution_events` table and management endpoints consumed by Tasks 2-6.

- [ ] **Step 1: Write the failing schema/config test**

Add a Spring test that asserts the Flyway schema contains `review_execution_events`, indexes it by `(project_id, created_at)` and `(task_id, created_at)`, and exposes `/actuator/health`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -Dtest=ObservabilitySchemaTest test` from `backened`.

Expected: FAIL because Actuator, the migration, and the table do not exist.

- [ ] **Step 3: Add the dependency and migration**

Add `spring-boot-starter-actuator` and `micrometer-registry-prometheus` to `pom.xml`. Configure `management.endpoints.web.exposure.include=health,prometheus`, `management.endpoint.health.show-details=never`, and the event retention property in `application.yml`. Create V10 with the exact columns from the approved spec and the two indexes.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -Dtest=ObservabilitySchemaTest test`.

Expected: PASS with Flyway applying V10 and Actuator health returning 200.

- [ ] **Step 5: Run the existing backend tests**

Run: `mvn -q test`.

Expected: PASS with no existing test regressions.

### Task 2: Java 事件实体、持久化和聚合服务

**Files:**
- Create: `backened/src/main/java/com/codeops/copilot/review/observability/ExecutionEventType.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/observability/ExecutionEventStatus.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/observability/ReviewExecutionEventEntity.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/observability/ReviewExecutionEventRepository.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/observability/ReviewExecutionEventService.java`
- Test: `backened/src/test/java/com/codeops/copilot/review/observability/ReviewExecutionEventServiceTest.java`

**Interfaces:**
- `ReviewExecutionEventService.record(ExecutionEventCommand command)` writes one best-effort event.
- `ReviewExecutionEventService.overview(Instant from, Instant to, Set<Long> projectIds)` returns task counts, success rate, average durations, LLM/tool totals, tokens, cost, retries, and pending outbox count.
- `ReviewExecutionEventService.timeline(String taskId)` returns ordered task/group/LLM/tool events without sensitive metadata.

- [ ] **Step 1: Write failing service tests**

Cover event persistence, duration aggregation, project filtering, and the rule that a repository failure is logged and does not escape `record`.

- [ ] **Step 2: Run focused tests and verify failure**

Run: `mvn -q -Dtest=ReviewExecutionEventServiceTest test`.

Expected: FAIL because the observability package does not exist.

- [ ] **Step 3: Implement the event model and service**

Use enums for event type/status, nullable boxed numeric fields, capped error/metadata strings, and repository queries grouped by event type/status. Keep SQL aggregation in repository methods so the API does not load all events into memory.

- [ ] **Step 4: Run focused tests**

Run: `mvn -q -Dtest=ReviewExecutionEventServiceTest test`.

Expected: PASS, including authorization project filtering.

### Task 3: Python LLM 和工具调用指标

**Files:**
- Modify: `llm-backend/app/models.py`
- Modify: `llm-backend/app/reviewer.py`
- Modify: `llm-backend/app/tools.py`
- Modify: `llm-backend/app/main.py`
- Modify: `llm-backend/app/config.py`
- Test: `llm-backend/tests/test_observability.py`
- Modify: `llm-backend/tests/test_reviewer.py`

**Interfaces:**
- Add `ToolCallMetric(name, duration_ms, status, error_code)` and `ReviewMetrics(model, duration_ms, input_tokens, output_tokens, total_tokens, estimated_cost, tool_calls)`.
- Add `ReviewRun(findings, metrics)` as the internal result of `AiReviewer.review_with_metrics(...)`.
- Keep `AiReviewer.review(...)` returning only findings for compatibility; it delegates to `review_with_metrics`.
- Extend `ReviewResponse` with optional `metrics`; extend `AiReviewRequest` with optional `task_id` and `group_number` correlation fields that are never included in prompts.

- [ ] **Step 1: Write failing Python tests**

Use fake LangChain messages containing `usage_metadata` and tool-call responses. Assert PLAN and REVIEW durations, token extraction from both supported metadata shapes, tool success/failure, unknown-token behavior, and configured per-model cost calculation.

- [ ] **Step 2: Run focused tests and verify failure**

Run: `python -m pytest tests/test_observability.py -q` from `llm-backend`.

Expected: FAIL because metrics models and instrumentation do not exist.

- [ ] **Step 3: Implement metrics collection**

Wrap each model invocation and each tool invocation with a monotonic timer. Sanitize tool metadata to names/status/error codes. Extract usage without failing the review when a provider omits usage. Return metrics from FastAPI while preserving the current findings schema.

- [ ] **Step 4: Run all Python tests**

Run: `python -m pytest -q` from `llm-backend`.

Expected: PASS with the existing reviewer/context/rules tests unchanged.

### Task 4: Java Worker、LLM 关联和 RabbitMQ 指标

**Files:**
- Modify: `backened/src/main/java/com/codeops/copilot/review/agent/CentralReviewService.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/tasks/ReviewTaskWorker.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/tasks/ReviewOutboxPublisher.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/tasks/ReviewQueueConfiguration.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/observability/QueueMetricsConfiguration.java`
- Test: `backened/src/test/java/com/codeops/copilot/review/tasks/ReviewTaskWorkerObservabilityTest.java`

**Interfaces:**
- Preserve `CentralReviewService.review(request, threshold)` and add a correlation-aware overload used by the Worker.
- Parse the Python `metrics` response into immutable Java records.
- Record task/group START, SUCCESS, FAIL, RETRY and CANCELLED events with taskId/projectId/groupNumber.
- Record LLM PLAN/REVIEW events and nested TOOL events using the returned metrics.

- [ ] **Step 1: Write failing Worker tests**

Assert that one successful group produces group and LLM events, a failed call produces a failed event and retry metric, and queue depth gauges are registered without task-specific labels.

- [ ] **Step 2: Run focused tests and verify failure**

Run: `mvn -q -Dtest=ReviewTaskWorkerObservabilityTest test`.

Expected: FAIL because CentralReviewService has no metrics contract and Worker emits no events.

- [ ] **Step 3: Implement correlation and instrumentation**

Send taskId/groupNumber as optional JSON correlation fields to Python. Inject `ReviewExecutionEventService` and Micrometer meters into Worker/Publisher. Wrap event recording in best-effort calls so observability failures never fail the review transaction.

- [ ] **Step 4: Run backend tests**

Run: `mvn -q test`.

Expected: PASS, including existing task retry/cancel tests.

### Task 5: 监控 API 和权限过滤

**Files:**
- Create: `backened/src/main/java/com/codeops/copilot/review/observability/ObservabilityController.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/observability/ObservabilityView.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/tasks/ReviewTaskController.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/agent/AgentAccessService.java`
- Modify: `backened/src/test/java/com/codeops/copilot/review/tasks/ReviewTaskControllerTest.java`
- Create: `backened/src/test/java/com/codeops/copilot/review/observability/ObservabilityControllerTest.java`

**Interfaces:**
- `GET /api/observability/overview?from=&to=&projectId=`
- `GET /api/observability/timeseries?from=&to=&projectId=&metric=`
- `GET /api/observability/queue`
- `GET /api/review-tasks/{taskId}/execution`

- [ ] **Step 1: Write failing controller tests**

Cover admin all-project access, ordinary-user project filtering, 403 for an unauthorized task execution request, empty time ranges, and stable response field names.

- [ ] **Step 2: Run focused tests and verify failure**

Run: `mvn -q -Dtest=ObservabilityControllerTest test`.

Expected: FAIL because the routes and view records do not exist.

- [ ] **Step 3: Implement the controllers and DTOs**

Reuse the existing session/agent principal resolution. Resolve accessible project IDs before querying. Return 403 without revealing whether an unauthorized task exists. Add task execution data to the existing detail response through a dedicated `execution` field or endpoint while preserving current fields.

- [ ] **Step 4: Run backend tests**

Run: `mvn -q test`.

Expected: PASS.

### Task 6: Vue 监控页面、任务时间线和 Docker 验证

**Files:**
- Modify: `frontend/src/api/reviewApi.js`
- Modify: `frontend/src/navigation.js`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/styles.css`
- Create: `frontend/src/observabilityState.js`
- Test: `frontend/src/observabilityState.test.js`
- Modify: `frontend/src/App.test.js`
- Modify: `docker-compose.yml`
- Create: `ops/prometheus/prometheus.yml`
- Modify: `README.md`

**Interfaces:**
- Add API clients for overview, timeseries, queue, and task execution.
- Add the `observability` route and a role-aware monitoring page.
- Keep task detail polling behavior; stop polling for terminal tasks.

- [ ] **Step 1: Write failing frontend tests**

Test metric formatting, percentage/cost formatting, empty/error states, route recognition, and rendering of a task timeline containing PLAN, REVIEW, and tool calls.

- [ ] **Step 2: Run focused frontend tests and verify failure**

Run: `npm test -- --runInBand` from `frontend`.

Expected: FAIL because observability state, route, and components do not exist.

- [ ] **Step 3: Implement the monitoring UI**

Add overview cards, project/time filters, hourly trend rows, queue status, failed-task list, and execution timeline. Use existing panel, status badge, pagination, and select styles. Do not render raw metadata or code content from events.

- [ ] **Step 4: Add optional Prometheus profile and documentation**

Add a Prometheus service under the `observability` Compose profile scraping the Java Actuator endpoint. Document profile startup, endpoint access, retention configuration, and the new monitoring page.

- [ ] **Step 5: Run frontend verification**

Run: `npm test -- --runInBand` and `npm run build` from `frontend`.

Expected: all tests pass and the production build succeeds.

- [ ] **Step 6: Run end-to-end Docker verification**

Run: `docker compose up -d --build`; verify `docker compose ps`, `/actuator/health`, `/actuator/prometheus`, and the monitoring API. Then run `docker compose --profile observability up -d` and verify Prometheus can scrape Java metrics.

Expected: all default services are healthy, the optional Prometheus service is healthy, and creating one review task produces task/group/LLM/tool events visible in the UI.
