# User Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver administrator-only browser workflows to create and manage users, Local Client devices, and project access.

**Architecture:** Extend the agent access service with admin-scoped summary and mutation methods. Keep browser API calls in `reviewApi.js` and render management state in an isolated Vue component that App routes to only for administrators.

**Tech Stack:** Java 21, Spring MVC/JPA, Flyway schema, Vue 3, Vitest.

**Spec:** `docs/superpowers/specs/2026-09-05-user-management-design.md`

## Global Constraints

- Never return Token plaintext except from `POST /api/management/users/{id}/agents`.
- Never store a Token plaintext server-side.
- Do not physically delete users or review history.
- Restrict all management routes and browser controls to administrators.

### Task 1: Management Read And Mutation APIs

**Files:** modify agent entities, repositories, `AgentAccessService`, `AgentManagementController`; add MVC tests.

- [ ] Write failing MVC tests for non-admin rejection, token-free user summaries, user deactivation, device revocation, and membership replacement.
- [ ] Run `mvn test -Dtest=AgentManagementControllerTest` and confirm the missing endpoints fail.
- [ ] Implement minimal service/repository/controller support for the documented endpoints and self-admin guard.
- [ ] Re-run `mvn test -Dtest=AgentManagementControllerTest` and confirm it passes.

### Task 2: Admin Browser API And Page

**Files:** modify `frontend/src/api/reviewApi.js`, `App.vue`, navigation; create `frontend/src/components/UserManagement.vue`; add Vue tests.

- [ ] Write failing tests for admin-only navigation and one-time device token rendering.
- [ ] Run `npm test -- --run` and confirm the tests fail before UI implementation.
- [ ] Implement typed API wrappers, route, management component, form validation, and mutation refreshes.
- [ ] Re-run `npm test -- --run` and confirm the tests pass.

### Task 3: Integration Verification

**Files:** modify README only if operation changed.

- [ ] Run `mvn test` in `backened`.
- [ ] Run `npm test -- --run` and `npm run build` in `frontend`.
- [ ] Confirm no list/detail payload exposes Token plaintext or `tokenHash`.
