# CodeOps Copilot

CodeOps Copilot is a Spring Boot MVP for AI-assisted Java pull request reviews.
The first version demonstrates the core workflow locally without requiring a GitHub token or an LLM API key:

```text
POST /api/reviews -> PENDING -> async processing -> GET /api/reviews/{id}
```

The review pipeline combines deterministic rules with a replaceable `AiReviewer` interface:

- `SensitiveDataRule` detects credentials and authorization values.
- `TodoCommentRule` detects unresolved TODO/FIXME markers.
- `SimulatedAiReviewer` demonstrates architecture and maintainability findings.
- `ReviewTaskRepository` is currently in-memory and can later be replaced with PostgreSQL.

## Run

Requires Java 21 and Maven 3.9+.

```powershell
mvn spring-boot:run
```

## Demo

```powershell
$body = @'
{
  "repository": "acme/order-service",
  "pullRequestNumber": 42,
  "title": "Add payment endpoint",
  "files": [
    {
      "path": "PaymentController.java",
      "content": "String token = request.getHeader(\"Authorization\");\\n// TODO add validation"
    }
  ]
}
'@
$task = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/reviews -ContentType 'application/json' -Body $body
Invoke-RestMethod -Uri "http://localhost:8080/api/reviews/$($task.id)"
```

## Local Git review

The Vue frontend and Java backend are intended to run on the same machine for this workflow. The backend process must be able to access the local repository path because it runs the Git commands and reads the selected files. Windows paths can be sent as JSON with escaped backslashes, or as forward-slash paths such as `D:/development/project/my_learn`.

Scan the working tree before submitting selected files:

```powershell
$scanBody = @{
  repositoryPath = 'D:/development/project/my_learn'
  scope = 'WORKTREE'
  baseRef = $null
} | ConvertTo-Json
$scan = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/repositories/scan `
  -ContentType 'application/json' -Body $scanBody
```

For a comparison against a branch or commit, use `scope = 'BASE_COMMIT'` and provide a non-empty `baseRef`. Submit only relative paths returned by the scan:

```powershell
$reviewBody = @{
  repositoryPath = 'D:/development/project/my_learn'
  scope = 'WORKTREE'
  baseRef = $null
  title = 'Review selected local changes'
  files = @('frontend/src/App.vue')
} | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/reviews/from-git `
  -ContentType 'application/json' -Body $reviewBody
```

The current engine is intentionally a local MVP: deterministic rules plus `SimulatedAiReviewer`. It does not call a production LLM yet. The Git scan and selected-file submission are real; only the review reasoning remains simulated until an `AiReviewer` implementation is connected.

## Test

```powershell
mvn test
```

## Next milestones

1. Replace the simulated reviewer with Spring AI and structured model output.
2. Add PostgreSQL/pgvector for project standards and historical PR retrieval.
3. Add GitHub webhook signature verification and PR comment publishing.
4. Move task execution to Kafka or RabbitMQ with retry and dead-letter handling.
