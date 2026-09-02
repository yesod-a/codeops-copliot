# CodeOps Copilot

CodeOps Copilot is a Java backend plus Vue frontend for AI-assisted pull request reviews. It combines deterministic checks with a replaceable AI reviewer and presents findings as a structured engineering report.

## Run the backend

Requires Java 21 and Maven 3.9+.

```powershell
cd backened
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

## Run the frontend

Requires Node.js 20+.

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL shown in the terminal. Requests to `/api` are proxied to the backend. When the backend is unavailable, the page keeps working with a local preview so the workflow can still be demonstrated.

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
POST /api/reviews
GET  /api/reviews/{id}
```
