# Asynchronous Review Tasks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace synchronous LLM review requests with durable RabbitMQ-backed review tasks that expose progress, cancellation, retry, and final history results.

**Architecture:** MySQL stores immutable task snapshots, groups, results, and an outbox event atomically. RabbitMQ carries task IDs; a worker claims and processes groups sequentially, then writes the existing review history. The frontend and Git Hook create tasks and query task state instead of waiting for LLM output.

**Tech Stack:** Spring Boot 3, Spring AMQP, MySQL 8/Flyway, RabbitMQ 3, Vue 3, Vitest, PowerShell/Pester, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-06-async-review-tasks-design.md`

## Global Constraints

- Preserve current project authorization and remote repository identity checks.
- Queue messages contain a task ID only; Git patches remain in MySQL.
- A Git Hook returns after task submission and always allows a successfully submitted asynchronous task.
- Use test-first implementation for all behavior changes.

---

### Task 1: Infrastructure and Domain Persistence

**Files:** Flyway migration, task/group/outbox entities and repositories, Maven AMQP dependency, Docker Compose.

- [ ] Write persistence tests for task creation, grouping, state transition, and outbox rows.
- [ ] Run the tests and verify they fail before task types exist.
- [ ] Add Flyway tables, task domain types, repositories, RabbitMQ dependency, and Docker services.
- [ ] Run persistence tests and Flyway-backed application tests.

### Task 2: Task Creation and Query APIs

**Files:** asynchronous review service/controller, agent controller, session controller, API tests.

- [ ] Write API tests for accepted creation, unauthorized creation, paging, task detail, cancellation, and retry.
- [ ] Run tests and verify missing endpoints fail.
- [ ] Create task snapshots and groups transactionally, enforce authorization, return `202`, and expose authorized task APIs.
- [ ] Run backend tests.

### Task 3: RabbitMQ Outbox Publisher and Worker

**Files:** AMQP configuration, outbox publisher, worker, retry classifier, central review adapter, tests.

- [ ] Write tests for publisher idempotency, worker group progress, transient retry, terminal failure, cancellation, and final history aggregation.
- [ ] Run tests and verify worker behavior fails before implementation.
- [ ] Implement publisher confirms, manual-ack consumer, lease-based claim, grouped LLM calls, delay queues, and history persistence.
- [ ] Run backend tests and container integration smoke checks.

### Task 4: Client Hook and Frontend Task Center

**Files:** PowerShell Hook/module/tests; Vue API client, task view/component/tests, navigation/App styles.

- [ ] Write Pester and Vitest tests for accepted Hook tasks, progress polling, cancel/retry actions, and history linking.
- [ ] Run tests and verify synchronous assumptions fail.
- [ ] Submit Hook tasks asynchronously; add paginated task list/detail, progress timeline, cancel/retry actions, and terminal-history links.
- [ ] Run frontend, Pester, and full backend tests.

### Task 5: Observability and Deployment Verification

**Files:** LLM error classifier, Docker configuration tests, README.

- [ ] Write tests for classified safe provider errors and Compose RabbitMQ/worker configuration.
- [ ] Implement safe error responses, structured task logs, metrics-ready task events, and operational documentation.
- [ ] Build all Docker images, validate container health and RabbitMQ wiring, and run regression suites.
