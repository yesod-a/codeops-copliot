# User Management Design

## Goal

Allow administrators to create, view, update, deactivate, and provision Local Client devices for users from the browser.

## Authorization And Data

All `/api/management/**` endpoints require an authenticated administrator session or administrator Agent Token. A user has a global role and an `active` state. A disabled user cannot sign in or authenticate a Local Client. Each device token has a separate `active` state and is stored only as a SHA-256 hash. The token plaintext is returned only by the create-device response.

## API

`GET /api/management/users` returns user summaries with device metadata and project memberships, never token hashes or token plaintext. `POST /users` creates a user. `PUT /users/{id}` updates display name, global role, and active state. `POST /users/{id}/agents` creates a device token. `PATCH /users/{id}/agents/{agentId}` changes a device token active state. `PUT /projects/{projectId}/members/{userId}` grants or updates a project role; `DELETE` removes that membership.

## Browser Experience

Only administrators see a User Management navigation entry. The page lists users, provides a user editor, exposes device metadata and token creation with one-time plaintext display, supports device revocation, and lets administrators set project memberships. Failure messages remain in the page; raw Token text is not retained after closing its dialog.

## Safety

Deletion is intentionally omitted. Deactivation keeps review history intact and invalidates authentication. An administrator cannot deactivate or demote their own account through this page, preventing accidental loss of administrative access.

## Verification

Spring MVC tests cover authorization, summary privacy, user update/deactivation, device-token revocation, and membership updates. Vue tests cover administrator navigation, user creation, and one-time token display/revocation behavior.
