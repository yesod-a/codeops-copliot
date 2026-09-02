# Local Git Review Design

## Goal

Extend CodeOps Copilot so a developer can review real local Git changes. The Java backend and Vue frontend run on the same machine. The developer enters a local repository path, scans the repository, chooses one or more changed files, and submits only those changes for asynchronous review.

The existing manual paste workflow remains available as a fallback. The current deterministic rules and simulated AI reviewer remain the review engine for this iteration; connecting a production LLM is out of scope.

## User Flow

1. The developer opens the review desk and selects `Local Git` mode.
2. The developer enters an absolute local repository path and chooses a diff scope.
3. The frontend calls the repository scan endpoint.
4. The backend validates the path, reads Git metadata and changed files, and returns a normalized file list with diff statistics and patches.
5. The developer selects one or more files and previews their patches.
6. The frontend submits the repository path, diff scope, selected relative paths, and review title.
7. The backend revalidates the repository and selected paths, reads the selected changes, creates a review task, and processes it asynchronously.
8. The existing status polling and structured findings view display the result.

## Diff Scope

The first version supports these scopes:

- `WORKTREE`: `git diff HEAD` plus staged changes, with untracked files included.
- `BASE_COMMIT`: `git diff <baseRef>...HEAD` for a user-provided base ref, with the same repository validation.

The scan response is authoritative for the selection UI, but the review submission re-runs the Git read. This prevents stale or modified selections from being trusted blindly.

## Backend Design

### Components

- `GitRepositoryService`: validates repository paths and orchestrates Git metadata and diff reads.
- `GitCommandRunner`: executes Git through `ProcessBuilder` argument arrays, never through a shell command string. It enforces a timeout and captures stdout/stderr.
- `GitDiffParser`: converts unified diff output and status metadata into normalized changed-file records.
- `ReviewController`: exposes repository scan and Git-backed review submission endpoints.
- Existing `ReviewService`: continues to own task creation, asynchronous processing, rules, and AI reviewer orchestration.

### Endpoints

`POST /api/repositories/scan`

Request:

```json
{
  "repositoryPath": "D:\\workspace\\order-service",
  "scope": "WORKTREE",
  "baseRef": null
}
```

Response:

```json
{
  "repositoryPath": "D:\\workspace\\order-service",
  "branch": "feature/payment",
  "headCommit": "abc1234",
  "files": [
    {
      "path": "src/main/java/com/acme/PaymentService.java",
      "status": "MODIFIED",
      "additions": 8,
      "deletions": 2,
      "patch": "@@ -10,2 +10,8 @@"
    }
  ]
}
```

`POST /api/reviews/from-git`

Request:

```json
{
  "repositoryPath": "D:\\workspace\\order-service",
  "scope": "WORKTREE",
  "baseRef": null,
  "title": "Review local changes",
  "files": ["src/main/java/com/acme/PaymentService.java"]
}
```

The endpoint converts selected Git files into the existing `ReviewRequest` model. Each `ChangedFile` contains the repository-relative path and the selected file content or normalized patch context used by the current rules.

### Validation and Limits

- The path must exist, be a directory, and resolve to a Git work tree.
- The normalized path must not escape the repository root.
- Selected file paths must be relative paths returned by the scan or valid current Git paths.
- Git commands have a fixed timeout.
- The scan and review have maximum file-count, patch-size, and file-size limits.
- Binary files and unsupported file types are marked or skipped with an explicit reason.
- Git stderr is returned as a safe user-facing error without exposing command internals.

## Frontend Design

The review form becomes a two-mode interface:

- `Local Git`: repository path, scope, base ref when needed, scan action, changed-file selection, patch preview, submit action.
- `Manual paste`: preserve the current single-file fallback form.

The local mode uses a stable file list with checkboxes, status badges, additions/deletions, select-all behavior, and an empty state when there are no changes. Submitting is disabled until at least one file is selected. Existing polling, risk metrics, finding filters, local preview fallback, and Markdown export remain intact.

The browser does not receive an absolute directory path from a native folder picker. The local mode therefore uses an explicit path field because the backend process is the component that can access the Git working tree.

## Error Handling

- Invalid path: show a clear repository path validation message.
- Non-Git directory: explain that `.git` or a Git work tree could not be found.
- No changes: show an empty result and keep submit disabled.
- Scan timeout or Git failure: show the Git error and preserve the entered path.
- Repository changes between scan and submit: re-scan and reject missing selections with a refresh action.
- Backend unavailable: preserve the existing local preview behavior only for manual paste mode; Git mode reports that the local backend must be running.

## Testing

Backend tests:

- Git command argument and timeout behavior through a replaceable runner.
- Repository validation and path traversal rejection.
- Unified diff parsing for modified, added, deleted, renamed, binary, and untracked files.
- Scan endpoint response and validation errors.
- Git review submission creates a task with only selected files.
- Existing review controller and service tests remain green.

Frontend tests:

- Local Git mode renders scan controls and selected-file state.
- Select-all and individual selection behavior.
- Submit remains disabled with no selected file.
- Scan and review API payloads contain the selected relative paths.
- Manual paste mode remains functional.

Browser verification covers desktop and mobile layouts, scan error state, file selection, patch preview, and a real local repository review against this project.

## Out of Scope

- GitHub API and webhook integration.
- Production LLM provider configuration.
- Persistent repository history and database storage.
- A native OS directory picker or standalone CLI agent.
- Posting findings back to GitHub.
