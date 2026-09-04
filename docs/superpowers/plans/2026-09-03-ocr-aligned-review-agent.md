# OCR-Aligned Review Agent Implementation Plan

> **For agentic workers:** Implement task-by-task with TDD and verify each task before moving on.

**Goal:** Upgrade the current one-shot LLM review flow into a safer, context-aware review pipeline inspired by OpenCodeReview while preserving the existing Vue, Java, Python, and review-history contracts.

**Architecture:** Java remains responsible for Git validation, current-content re-reading, and persistence. The Python `llm-backend` becomes the review orchestration layer: it resolves path rules, builds bounded repository context, runs a small/large-change review strategy, and validates structured findings. The frontend submits selected relative paths through the Java Git-backed endpoint instead of trusting stale scan patches.

**Tech Stack:** Spring Boot/Java, FastAPI/Python, LangChain ChatOpenAI, Vue/Vite, JUnit, pytest, Vitest.

**Spec:** Approved in-chat OCR-aligned architecture discussion on 2026-09-03.

## Global Constraints

- Preserve existing manual-paste review behavior.
- Keep repository access inside the validated Git work tree.
- Do not expose provider errors or API keys to clients.
- Every selected file must be re-read at submit time and accounted for.
- Findings must remain compatible with the existing history schema.

---

### Task 1: Submit current Git content through Java

**Files:**
- Modify: `frontend/src/api/reviewApi.js`
- Modify: `frontend/src/components/ReviewForm.vue`
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewController.java`
- Test: existing frontend API/component tests and Java controller tests

- [ ] Add a failing test proving Git review submission sends selected relative paths to `/api/reviews/from-git`.
- [ ] Implement the API call and make the form emit selected paths rather than trusting patch content.
- [ ] Verify Java re-reads the current snapshot and rejects stale or unsupported selections.

### Task 2: Add project rule resolution and bounded context helpers

**Files:**
- Create: `llm-backend/app/rules.py`
- Create: `llm-backend/app/context.py`
- Modify: `llm-backend/app/models.py`
- Test: `llm-backend/tests/test_rules.py`, `llm-backend/tests/test_context.py`

- [ ] Add failing tests for glob-based rules, default rules, path traversal rejection, bounded file reads, and code search limits.
- [ ] Implement project-local `.opencodereview/rule.json` resolution and safe repository context helpers.
- [ ] Verify context helpers never read outside the repository root.

### Task 3: Replace one-shot review with staged orchestration

**Files:**
- Modify: `llm-backend/app/prompts.py`
- Modify: `llm-backend/app/reviewer.py`
- Modify: `llm-backend/app/main.py`
- Test: `llm-backend/tests/test_reviewer.py`, `llm-backend/tests/test_api.py`

- [ ] Add failing tests for per-file review, conditional plan context for large changes, and structured output validation.
- [ ] Implement grouped review preparation, bounded context injection, and optional second-pass review without requiring a new provider API.
- [ ] Keep provider failures mapped to the existing 503 response.

### Task 4: Validate and normalize line-level findings

**Files:**
- Create: `llm-backend/app/findings.py`
- Modify: `llm-backend/app/models.py`
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewFinding.java`
- Test: `llm-backend/tests/test_findings.py`, Java persistence/controller tests

- [ ] Add failing tests for invalid paths, out-of-range lines, missing evidence, and severity normalization.
- [ ] Implement finding normalization and line validation against supplied file content/patches.
- [ ] Preserve the existing `line` field while adding optional `start_line`/`end_line` compatibility fields only where safe.

### Task 5: Verify the vertical slice

- [ ] Run Python tests with `pytest -q`.
- [ ] Run frontend tests with `npm test -- --run` from `frontend`.
- [ ] Run Java tests with `mvn -q test` from `backened`.
- [ ] Inspect the final diff and report any existing unrelated failures separately.

