# CodeOps Copilot Central API

The Java service is the central API for authentication, project policy, rules, review history, and authenticated Git Hook review requests. In a shared deployment it does not read a developer workstation path. The local CodeOps Client reads Git changes and sends relative paths and patches to `POST /api/agent/reviews`.

## Container deployment

Run the complete stack from the repository root. The `backend` container connects to MySQL and `llm-backend` through the Compose network.

```powershell
Copy-Item .env.example .env
# Set AI_API_KEY and CODEOPS_BOOTSTRAP_TOKEN in .env.
docker compose up -d --build
```

The frontend and public API are served through `http://127.0.0.1:5173`. The Java API is intentionally not published as a separate host port.

## Central API

```text
POST /api/auth/login
POST /api/auth/logout
GET  /api/auth/me
POST /api/projects/register
GET  /api/projects
PUT  /api/projects/{id}/policy
POST /api/client/projects/resolve
POST /api/agent/reviews
POST /api/management/bootstrap
POST /api/management/users
POST /api/management/users/{userId}/agents
POST /api/management/projects/{projectId}/members
```

`POST /api/projects/register` accepts a `name` and `remoteUrl`. The server normalizes HTTPS, SCP-style SSH, and `ssh://` URLs to a repository key such as `git.example.com/acme/order-service`; it does not inspect a local filesystem.

`POST /api/client/projects/resolve` accepts `{"remoteUrl":"..."}` and an Agent Token. It returns the central project identity and effective review policy only for an administrator, owner, or reviewer. An unregistered remote returns a disabled response without a project identifier; a viewer or non-member receives `403`.

The supported developer-side integration is the Java 21 Local Client in `../local-client`. It binds only to `127.0.0.1`, holds the Agent Token in user-scoped DPAPI storage, resolves the Git remote before every review, and submits only remote-derived repository identity and relative Git changes. The PowerShell Git Hook is only a localhost adapter and does not authenticate with this API directly.

`POST /api/agent/reviews` requires the submitted `repositoryKey` to match the selected central project before the LLM is invoked. The older `/api/projects/import` and `/api/repositories/*` endpoints are retained only for host-local development.

## Tests

```powershell
mvn test
```
