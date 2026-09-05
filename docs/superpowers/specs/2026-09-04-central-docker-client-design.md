# Central Docker Service and Local Git Client Design

## Goal

Deploy every server-side CodeOps component through Docker while keeping Git repository discovery, diff creation, and push interception on each developer machine.

## Boundaries

The central server consists of the Vue frontend, Spring Boot API, LangChain LLM backend, and MySQL. It never receives or stores a developer's absolute repository path. The Spring API is reachable through the frontend's `/api` reverse proxy and calls the LLM backend over the Compose network.

The local client is a PowerShell Git Hook distribution. It reads Git state locally, sends the project id, repository key, branch, commit references, relative file paths, line counts, and patches to `POST /api/agent/reviews`, then returns a non-zero process exit code when the response is blocked. It does not perform LLM requests or access server-side databases.

## Deployment

`docker-compose.yml` must create four application services: `mysql`, `llm-backend`, `backend`, and `frontend`. The backend waits for healthy MySQL and LLM services. Its datasource and LLM endpoint use service DNS names, not `127.0.0.1` or `host.docker.internal`. The frontend proxies all `/api/` calls to `backend:8080`.

The Compose file exposes only the frontend and optional operational ports declared in `.env`. Secrets are supplied through environment variables and are not committed. `CODEOPS_BOOTSTRAP_TOKEN` is mandatory for production initialization and must not retain the insecure fallback value.

## Client Configuration

Each reviewed Git repository stores client configuration at `.codeops/client.json`, which must be ignored by Git. The configuration format is:

```json
{
  "serverUrl": "https://codeops.example.com",
  "projectId": 3,
  "agentToken": "cop_...",
  "timeoutSeconds": 600,
  "failOpen": false
}
```

Environment variables retain precedence over the configuration file for automation: `CODEOPS_SERVER_URL`, `CODEOPS_PROJECT_ID`, `CODEOPS_AGENT_TOKEN`, `CODEOPS_REVIEW_TIMEOUT_SECONDS`, and `CODEOPS_REVIEW_FAIL_OPEN`.

The install script installs global Git hook launchers. The hook locates the current repository root and loads the repository-local configuration. A new initialization script validates the configuration fields, creates `.codeops/client.json` with UTF-8 encoding, adds it to `.git/info/exclude` where possible, and confirms that the user can choose a project only through a valid Agent Token at review time. An uninstall script removes CodeOps-managed hooks only when `core.hooksPath` still points at the CodeOps directory.

## Security and Failure Behavior

The client sends no absolute `repositoryPath`. The server authorizes each request with the Agent Token and project membership before LLM invocation. Tokens remain local and are never exposed through the web frontend after creation. Pre-push and pre-commit are fail-closed by default; explicit client configuration or environment can enable fail-open. Post-merge is advisory and never blocks a completed merge.

## Compatibility

The former legacy local mode remains usable only when no central client configuration or central environment configuration is present. Docker deployment documentation presents central mode as the supported multi-user deployment. Existing Hook tests continue to cover legacy helper functions and gain configuration precedence coverage.

## Verification

1. `docker compose config` renders `backend` and container-internal upstream URLs.
2. Backend unit tests verify agent authorization remains required.
3. Pester tests verify a repository configuration activates central mode and environment values override it.
4. A dry-run push through the Hook uploads only relative paths and respects the server's `blocked` response.
