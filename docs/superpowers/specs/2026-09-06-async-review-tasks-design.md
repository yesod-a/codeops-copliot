# Asynchronous Review Tasks

## Goal

Allow Git pushes and manual reviews to create durable asynchronous review tasks, while preserving project authorization, review history, cancellation, retry, and visible progress.

## Architecture

The web backend creates a task, immutable Git diff snapshot, deterministic review groups, and an outbox event in one MySQL transaction. An outbox publisher sends only the task ID to RabbitMQ. A separately scaled worker consumes the task, reviews one group at a time through `llm-backend`, persists group findings and progress, and creates the existing review-history record only after all groups finish.

RabbitMQ is used because tasks are point-to-point commands needing manual acknowledgement, bounded concurrency, TTL retry queues, and dead-lettering. Kafka is not selected for this first phase because replayable high-volume event streams and partition-offset management are not required.

## Runtime

1. The Git Hook or web UI posts a review request to a task creation endpoint.
2. The backend authenticates the agent or session, verifies project membership, repository identity, policy, and input limits.
3. The backend returns `202 Accepted` with a task ID. The Git Hook allows the Git operation.
4. The worker claims one queued task and reviews its groups sequentially.
5. The worker publishes progress after each completed group and checks for cancellation before the next group.
6. Success writes one `ReviewHistory` result; blocked findings are represented by `outcome=BLOCKED`, not a task failure.

## Task Model

States are `QUEUED`, `RUNNING`, `RETRY_WAIT`, `CANCEL_REQUESTED`, `CANCELLED`, `COMPLETED`, and `FAILED`. `COMPLETED` has an `outcome` of `PASSED` or `BLOCKED`. A task has immutable source files, groups, completed group findings, progress counters, retry metadata, a safe error summary, and an optional review-history ID.

Cancellation is cooperative. Queued and retry-wait tasks cancel immediately. A running task completes or times out its current provider call, then stops before the next group. Retriable provider/network failures use 30-second, 2-minute, and 10-minute delays, with at most three attempts per group. Authentication, validation, and malformed-model-response failures are terminal.

## Reliability

The outbox is published with RabbitMQ publisher confirmations. The consumer uses manual acknowledgement and conditionally claims task/group state. Duplicate queue messages and worker restarts are harmless because state transitions are idempotent. An expired worker lease returns unfinished work to the queue. RabbitMQ dead letters exhausted or malformed messages for operator inspection.

## API and UI

`POST /api/agent/review-tasks` creates Hook tasks. `POST /api/review-tasks` creates session-authenticated manual tasks. `GET /api/review-tasks`, `GET /api/review-tasks/{id}`, `/cancel`, and `/retry` provide task management.

The frontend exposes an Assessment Tasks view separate from completed review history. It polls nonterminal task details every two seconds, shows group progress and retry/cancel events, exposes authorized actions, and links completed tasks to the existing history-detail screen.

## Security and Operations

Queue messages contain only task identifiers. Patches remain in MySQL. Task reads and actions enforce the existing project permission model. Public errors are sanitized; server logs include task ID, group number, attempt, duration, and classified provider error. Docker runs RabbitMQ, API backend, and a worker service using the same backend image with a worker profile.
