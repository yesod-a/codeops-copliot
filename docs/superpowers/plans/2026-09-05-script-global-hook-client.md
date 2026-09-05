# Script-only Global Hook Client Plan

1. Add a shared PowerShell module for user settings, DPAPI credentials, Git discovery/diff collection, UTF-8 HTTP, and central payloads.
2. Replace repository-local initialization with one-time machine-user initialization and automatic global Hook installation.
3. Make Hook execution resolve `origin`, apply central project policy, collect relative diffs, and issue one `/api/agent/reviews` request without local paths.
4. Preserve and restore a pre-existing global `core.hooksPath` through generated chain launchers.
5. Expand project resolution response with all trigger flags and fail-open policy.
6. Update README and add Pester coverage for token storage, payload privacy, resolution behavior, and script-only execution.

Verification:

- `Invoke-Pester .\scripts`
- `mvn test` from `backened`
- PowerShell parser validation and `git diff --check`
