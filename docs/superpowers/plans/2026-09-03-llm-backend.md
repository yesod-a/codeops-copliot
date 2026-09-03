# LLM Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a Python LangChain LLM review service and connect the Java review workflow to it over HTTP.

**Architecture:** The Python `llm-backend` owns model configuration, prompts, LangChain invocation, and JSON validation. Java keeps Git access, task lifecycle, deterministic rules, and an HTTP client boundary; Java no longer contains an AI reviewer implementation.

**Tech Stack:** Python 3.11+, FastAPI, Pydantic Settings, LangChain Core, LangChain OpenAI, Spring Boot 3.5, Java 21.

**Spec:** Approved chat design for a Python `llm-backend` with a class named `AiReviewer`.

## Global Constraints

- The Python service must support OpenAI-compatible endpoints through environment configuration.
- API keys must come from environment variables or an ignored `.env` file and must never be committed.
- The Python response must validate every finding before returning it.
- Java must keep deterministic rules and mark model failures as failed tasks.
- Existing local Git scan and manual review request formats remain unchanged.

---

### Task 1: Python reviewer core

**Files:**
- Create: `llm-backend/app/models.py`
- Create: `llm-backend/app/config.py`
- Create: `llm-backend/app/prompts.py`
- Create: `llm-backend/app/reviewer.py`
- Create: `llm-backend/tests/test_reviewer.py`
- Create: `llm-backend/requirements.txt`
- Create: `llm-backend/.env.example`

**Interfaces:**
- `AiReviewer.review(repository: str, title: str, files: list[ReviewFile]) -> list[ReviewFinding]`
- `ReviewFile(path: str, content: str)` and `ReviewFinding(category, severity, file, line, message, suggestion, evidence, confidence)` use validated Pydantic models.

- [ ] Write a failing test for valid JSON parsing and model invocation.
- [ ] Run `python -m pytest tests/test_reviewer.py -q` and observe the missing module failure.
- [ ] Implement the LangChain prompt, OpenAI-compatible model construction, JSON extraction, and Pydantic validation.
- [ ] Run the focused test and then the full Python test suite.
- [ ] Commit `feat: add langchain ai reviewer core`.

### Task 2: FastAPI review endpoint

**Files:**
- Create: `llm-backend/app/main.py`
- Create: `llm-backend/tests/test_api.py`

**Interfaces:**
- `POST /api/ai/review` accepts `{repository, title, files[]}` and returns `{findings[]}`.
- `GET /api/ai/health` returns provider/model readiness without exposing the API key.

- [ ] Write failing endpoint tests for a valid request and reviewer failure.
- [ ] Run the focused endpoint tests and observe the missing application failure.
- [ ] Implement request validation, dependency injection for `AiReviewer`, HTTP 200/503 responses, and safe error messages.
- [ ] Run all Python tests.
- [ ] Commit `feat: expose langchain review api`.

### Task 3: Java HTTP integration

**Files:**
- Modify: `backened/pom.xml`
- Create: `backened/src/main/java/com/codeops/copilot/review/ai/AiBackendProperties.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/ai/AiReviewClient.java`
- Create: `backened/src/main/java/com/codeops/copilot/review/ai/HttpAiReviewClient.java`
- Create: `backened/src/test/java/com/codeops/copilot/review/ai/HttpAiReviewClientTest.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewService.java`
- Modify: `backened/src/main/java/com/codeops/copilot/review/ReviewConfiguration.java`
- Modify: `backened/src/test/java/com/codeops/copilot/review/ReviewServiceTest.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/AiReviewer.java`
- Delete: `backened/src/main/java/com/codeops/copilot/review/SimulatedAiReviewer.java`

**Interfaces:**
- Java `AiReviewClient.review(ReviewRequest)` returns model findings and maps the Python response into existing `ReviewFinding` records.
- `AiBackendProperties` reads `ai.backend.enabled`, `ai.backend.base-url`, `ai.backend.connect-timeout`, and `ai.backend.read-timeout`.

- [ ] Write a failing client test for POST payload mapping and returned findings.
- [ ] Run the focused test and observe missing client/configuration classes.
- [ ] Implement the HTTP client with bounded timeouts and safe exception messages.
- [ ] Replace the Java AI interface in `ReviewService`, preserve deterministic rules, and remove the Java simulation classes.
- [ ] Run all Java tests.
- [ ] Commit `feat: connect java review workflow to llm backend`.

### Task 4: Configuration, docs, and integration verification

**Files:**
- Create: `backened/src/main/resources/application.yml`
- Modify: `backened/README.md`
- Create: `llm-backend/README.md`
- Modify: `.gitignore`
- Create: `scripts/start-llm-backend.ps1`

- [ ] Document environment variables, provider setup, startup order, request flow, and the no-key failure behavior.
- [ ] Add `.env` and Python cache/build exclusions.
- [ ] Run Java tests, Python tests, frontend tests/build, and a local integration request when a model key is configured.
- [ ] Commit `docs: document llm backend setup`.
