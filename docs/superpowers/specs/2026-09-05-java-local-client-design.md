# Java Local Client Design

## Purpose

Replace the repository-local `.codeops/client.json` and direct PowerShell Hook-to-central-service flow with an installed Java Local Client. The client runs once per Windows user, binds only to loopback, obtains the current repository identity from Git, and invokes the existing authenticated central APIs.

The central service remains the authority for project identity, membership, policy, review results, and blocking decisions. The Local Client never treats a cached project ID or local path as authorization.

## Scope

The first release provides a runnable Java 21 client with a localhost HTTP API, machine-level configuration, DPAPI-protected token storage, safe Git access, a command-line interface, and a thin PowerShell Hook adapter. It also includes WinSW configuration so the packaged JAR can run as a Windows Service.

The release does not add new central authentication flows, device registration APIs, a GUI, cross-platform service installation, arbitrary process execution endpoints, or server-side filesystem access.

## Architecture

`local-client/` is a standalone Maven module. It produces an executable Java JAR and does not import Spring Boot or classes from `backened/`.

```text
Git Hook
  -> POST http://127.0.0.1:49152/v1/reviews/pre-push
  -> Java Local Client
       -> fixed Git commands inside verified repository root
       -> POST central /api/client/projects/resolve
       -> POST central /api/agent/reviews
  -> blocked result
  -> Hook exit 0 or 1
```

The HTTP server is implemented with JDK `ServerSocket`, binds explicitly to `127.0.0.1`, and accepts JSON via Jackson. Central HTTP calls use blocking JDK `HttpURLConnection` so the client does not depend on the asynchronous JDK HTTP selector. The client starts with an empty configuration and refuses central requests until `login` stores a valid server URL and Agent Token.

## Persistent State

Machine-level data lives under `%LOCALAPPDATA%\CodeOps` (or the equivalent `LOCALAPPDATA` environment value):

```text
%LOCALAPPDATA%\CodeOps\client.json
%LOCALAPPDATA%\CodeOps\credentials.dat
%LOCALAPPDATA%\CodeOps\repositories\<sha256(remote-url)>.json
```

`client.json` contains non-secret settings only:

```json
{
  "serverUrl": "https://codeops.example.com",
  "listenAddress": "127.0.0.1",
  "listenPort": 49152,
  "requestTimeoutSeconds": 600
}
```

`credentials.dat` contains the Agent Token encrypted with Windows DPAPI under the current user context. The implementation invokes `powershell.exe` with a fixed embedded DPAPI helper script and receives or supplies the plaintext only through standard input and output; it never puts the token in a process argument or in a repository file. Running on a non-Windows platform reports that credential storage is unavailable.

The optional repository cache stores `remoteUrl`, resolved project metadata, and `lastResolvedAt`. It is optimization-only. Every review resolves the remote URL with the central service again before it sends a review.

## CLI

The executable entry point is `com.codeops.client.CodeOpsClientApplication`:

```text
codeops-client start
codeops-client login --server-url <https-url> --token-stdin
codeops-client status
codeops-client doctor
codeops-client install
codeops-client uninstall
```

`login` reads exactly one Agent Token line from standard input, validates that the server URL is HTTP or HTTPS with no query or fragment, stores non-secret settings in `client.json`, and stores the token with DPAPI.

`status` reports whether the configuration and encrypted credential are present and whether the loopback endpoint responds. It never prints the token.

`doctor` checks Java, Git, local configuration, loopback service health, and central service connectivity without creating a review.

`start` runs the loopback server in the foreground. `install` writes a WinSW XML file beside a chosen service wrapper and creates the global Git Hook files; it fails with a direct message if a WinSW executable has not been provisioned. `uninstall` removes only CodeOps-owned service and Hook resources after ownership checks.

## Local HTTP API

Every endpoint rejects requests whose socket peer is not a loopback address. JSON requests reject unknown or missing required fields. Responses have JSON content type and do not include exception stack traces, configuration paths, or tokens.

```text
GET  /health
GET  /v1/status
POST /v1/repositories/resolve
POST /v1/reviews/pre-push
POST /v1/reviews/pre-commit
POST /v1/reviews/post-merge
```

`/health` returns `{"status":"UP"}`. `/v1/status` exposes only service/configuration readiness.

`/v1/repositories/resolve` consumes:

```json
{"repositoryPath":"D:\\work\\order-service","remoteName":"origin"}
```

The client first runs `git rev-parse --show-toplevel`, then runs `git remote get-url <remoteName>` from that discovered root. It calls `/api/client/projects/resolve` with the remote URL and returns the central resolution without adding local absolute paths.

`/v1/reviews/pre-push` consumes a repository path, optional remote name, and an array of four-field Git pre-push update records. It rejects malformed reference updates and only reads Git data from the discovered root. For each non-deletion update it resolves a merge base, lists changes, collects permitted relative-path patches, resolves the central project, and submits the existing `POST /api/agent/reviews` request.

The response is:

```json
{"blocked":false,"message":"Central review passed","findings":[]}
```

An unregistered repository returns `200` with `blocked: false` and a skip message. A central `401` or `403`, malformed local request, unavailable client configuration, or unavailable central server is a failed operation. The client follows configured `failOpen`: false by default for pre-push and pre-commit, true for post-merge.

## Git and Path Safety

The client exposes no shell endpoint. It uses `ProcessBuilder(List<String>)`, never concatenated command strings, for an allowlist of Git subcommands:

```text
git rev-parse --show-toplevel
git remote get-url <remoteName>
git branch --show-current
git rev-parse HEAD
git merge-base <remote> <local-sha>
git diff --name-status --no-renames --no-ext-diff <base> <head> --
git diff --numstat --no-renames <base> <head> -- <relative-path>
git diff --no-ext-diff --unified=80 <base> <head> -- <relative-path>
```

The requested repository path must exist. The discovered Git root is normalized with `toRealPath()` and becomes the working directory for every Git invocation. Changed paths must be relative, normalized with `/`, must not be absolute, and must not escape the repository root after `root.resolve(path).normalize()`.

Binary paths (`.pyc`, image archives, and other existing hook exclusions) and patches larger than configured limits are skipped. The client sends only relative paths, Git metadata, and patches to the central API.

## Central API Contract

The client uses the existing endpoints and preserves their authority:

```text
POST /api/client/projects/resolve
Authorization: Bearer <Agent Token>
{"remoteUrl":"..."}

POST /api/agent/reviews
Authorization: Bearer <Agent Token>
{
  "projectId": 3,
  "repositoryKey": "git.example.com/acme/order-service",
  "trigger": "pre-push",
  "title": "pre-push review: ...",
  "branch": "feature/payment",
  "headCommit": "...",
  "baseRef": "...",
  "files": []
}
```

The Local Client cannot override the central policy or `failOnSeverity`; central response `blocked` is passed through as the authoritative review result.

## Hook Migration

`scripts/git-review-hook.ps1` becomes a thin adapter. It discovers the current repository root, collects raw pre-push stdin received from Git, posts it to `http://127.0.0.1:49152/v1/reviews/pre-push`, prints the response message and findings, and exits `1` only when the client returns `blocked: true` or a fail-closed error.

The hook does not read `.codeops/client.json`, access `CODEOPS_PROJECT_ID` or `CODEOPS_AGENT_TOKEN`, execute Git diffs, call the central API, or call the LLM. The old initialization script remains only as a documented legacy compatibility tool until the new installer has a migration path.

## Packaging and Service Installation

Maven produces `local-client/target/codeops-client.jar`. A release bundle supplies `codeops-client.exe` through `jpackage` and includes WinSW. WinSW runs:

```text
javaw -jar codeops-client.jar start
```

The service configuration runs under the logged-in user so DPAPI can decrypt that user's token. This avoids a machine service account being unable to read user-scoped credentials. The installer only binds loopback and does not create firewall rules.

## Tests and Verification

Unit tests cover:

- client configuration parsing and server URL validation;
- repository-root discovery, relative-path rejection, and fixed Git argument construction;
- remote-project resolution request construction and unregistered skip behavior;
- pre-push parsing, deletion skipping, central blocked response propagation, and fail-open behavior;
- localhost server health and rejected malformed requests;
- Hook forwarding and its exit-code mapping.

Integration verification starts the Java client on an ephemeral loopback port with a temporary Git repository and an in-process central HTTP stub. It verifies the remote URL is resolved, no local absolute path is sent to the central stub, and a blocked central response produces a non-zero Hook exit.
