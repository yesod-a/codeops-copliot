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

## Test

```powershell
mvn test
```

## Next milestones

1. Replace the simulated reviewer with Spring AI and structured model output.
2. Add PostgreSQL/pgvector for project standards and historical PR retrieval.
3. Add GitHub webhook signature verification and PR comment publishing.
4. Move task execution to Kafka or RabbitMQ with retry and dead-letter handling.
