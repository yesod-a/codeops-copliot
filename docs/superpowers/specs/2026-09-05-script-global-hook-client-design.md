# Script-only Global Git Hook Client

## Goal

Provide central Docker-hosted review for multiple developers without a resident client service and without sending local filesystem paths to the server.

## Runtime

1. An administrator grants a user an Agent Token and project review role.
2. The user runs `initialize-codeops-client.ps1` once. Settings and a DPAPI-protected token are stored under `%LOCALAPPDATA%\CodeOps`.
3. The script installs global `pre-commit`, `pre-push`, and `post-merge` launchers.
4. A launcher discovers the current repository with Git, reads `origin`, and calls `/api/client/projects/resolve`.
5. Registered, authorized projects send one review request to `/api/agent/reviews`. The request contains only repository key, branch/commit metadata, relative paths, and patches.
6. The server loads project/global rules, invokes the LLM workflow, persists the confirmed findings, and returns the blocking decision.

Unregistered remotes are skipped. Invalid tokens, missing project permission, disabled triggers, service failures under fail-closed policy, and blocking findings stop `pre-push`.

## Security

- Token data is encrypted with the current Windows user's DPAPI and is never written to a repository.
- Local absolute paths are used only by Git commands on the developer machine.
- Central project identity is the canonical remote repository key, not a workstation path or hand-entered project ID.
- Existing global hooks are recorded and invoked by the generated launchers; uninstall restores the prior `core.hooksPath`.
