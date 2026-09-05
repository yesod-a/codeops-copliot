# CodeOps Copilot

CodeOps Copilot is a centralized AI code review service with a local Git Hook client. Docker hosts the frontend, Java API, LangChain reviewer, and MySQL; each developer machine reads its own Git repository and uploads only relative paths and patches.

## Run all services with Docker

Requires Docker Desktop with Docker Compose.

Create the runtime configuration once:

```powershell
Copy-Item .env.example .env
# Edit .env and set AI_API_KEY
```

Start all four services in Docker:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-stack.ps1
```

Open `http://localhost:5173`. Nginx routes browser API calls to the Java container, and Java calls the LLM container through the Compose network. The server never receives a developer absolute path.

Stop the stack with:

```powershell
docker compose down
```

MySQL data is kept in the named `mysql-data` volume. The database uses host port `3307` by default.

## Central review mode

For a shared server deployment, the developer machine only runs the Git Hook client and uploads relative paths plus patches; it never calls the LLM directly.

Initialize the first administrator once (set `CODEOPS_BOOTSTRAP_TOKEN` on the server and use the same value in the request):

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:5173/api/management/bootstrap `
  -Headers @{ 'X-CodeOps-Bootstrap-Token' = $env:CODEOPS_BOOTSTRAP_TOKEN } `
  -ContentType 'application/json' -Body '{"username":"admin","displayName":"管理员","password":"change-this-password"}'
```

The response contains a one-time agent token. Use the bootstrap username and password in the web login page. An administrator can then create users (with a password), create additional agent tokens, and grant project roles through `/api/management/*`.

Open `http://localhost:5173` and sign in with the bootstrap account. Browser requests use a server-side HttpOnly Session cookie; local Git hooks continue to use their separate `cop_...` Agent Token.

For the current local development database, the available test accounts are:

```text
admin     / 123456   (administrator)
developer / 123456   (ordinary user)
```

These credentials are for local development only. Production deployments should use stronger, unique passwords.

## Central project identity

Register shared projects from their Git remote URL, never from a developer workstation path. The server normalizes HTTPS and SSH forms of the same remote to one repository key, for example `git.example.com/acme/order-service`.

```json
{
  "name": "Order Service",
  "remoteUrl": "https://git.example.com/acme/order-service.git"
}
```

An installed Git Hook client resolves its Git `origin` through `POST /api/client/projects/resolve` using its Agent Token. The response is disabled for unregistered repositories and is rejected for users without a review-capable project role. The central review endpoint also binds a review request to that resolved repository key before invoking the LLM.

## Install the script-only Git Hook client

The developer machine does not run a resident service or Java client. Run the initializer once for the Windows user that performs Git operations. It stores settings under `%LOCALAPPDATA%\CodeOps`, stores the Agent Token with Windows DPAPI, and installs global `pre-commit`, `pre-push`, and `post-merge` hooks.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\initialize-codeops-client.ps1 `
  -ServerUrl http://localhost:5173 `
  -AgentToken cop_...
```

The Hook discovers the current repository with `git rev-parse --show-toplevel`, reads `git remote get-url origin`, resolves the project and user permission through `/api/client/projects/resolve`, then sends the Git diff to `/api/agent/reviews`. Only remote identity, repository-relative paths, and patches are sent; no local absolute path or token is included in the JSON payload. Unregistered remotes are skipped. Registered projects without review permission, or high-severity findings, block `pre-push` by default.

Uninstall and restore the previous global Hook path with:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\uninstall-pre-push-hook.ps1
```

To change the local client settings, rerun the initializer with `-Force`:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\initialize-codeops-client.ps1 `
  -ServerUrl http://localhost:5173 `
  -AgentToken cop_... `
  -TimeoutSeconds 900 `
  -FailOpen -Force
```

The hook excludes binary artifacts such as `.pyc` and sends the central request as one review operation. The Python backend groups files by size internally and performs the OCR-style plan and review flow. Use `-FailOpen` or set the stored `failOpen` option only when you explicitly want to allow a push after a service failure. Git hooks can be bypassed with `git push --no-verify`, so branch protection and required CI checks should remain the final merge gate.

## Run the frontend

Requires Node.js 20+.

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL shown in the terminal. In Docker mode, browser and client requests use the frontend URL (`http://localhost:5173`); Nginx routes `/api/` to the internal Java service (`backend:8080`), and Java routes reviews to the internal LangChain service (`llm-backend:8090`). The Git review button sends the selected patch content directly to the configured API. When a service is unavailable, the page shows the corresponding connection error.

## Validate

```powershell
cd frontend
npm test
npm run build
```

```powershell
cd backened
mvn test
```

## API

```text
POST   /api/repositories/scan
POST   /api/ai/review
GET    /api/ai/health
POST   /api/reviews
GET    /api/reviews?limit=20&offset=0
GET    /api/reviews/{id}
DELETE /api/reviews/{id}
```

`/api/ai/*` is provided by the LangChain service on its internal port `8090`. In central mode Java on its internal port `8080` additionally authenticates local clients, calls the LLM service, applies project blocking policy, and persists review history. Only the frontend port is required for normal browser and hook access.
