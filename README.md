# CodeOps Copilot

CodeOps Copilot is a Java backend plus Vue frontend for AI-assisted code reviews. The LangChain Python service performs LLM reviews, while Java scans local Git repositories and persists review history in MySQL.

## Run the backend

Requires Java 21 and Maven 3.9+.

```powershell
cd backened
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

## Run all services with Docker

Requires Docker Desktop with Docker Compose.

Create the runtime configuration once:

```powershell
Copy-Item .env.example .env
# Edit .env and set AI_API_KEY
```

Start MySQL, the frontend, and Python LangChain backend with Docker, and start the Java backend locally in the same command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-local-stack.ps1
```

Open `http://localhost:5173`. The Nginx container routes `/api/repositories/*` to the Java service on the host at `http://host.docker.internal:8080`, and `/api/ai/*` to the Python service container. The workbench sends selected Git patches directly to `/api/ai/review`; Java remains responsible for local Git scanning.

Java runs directly on Windows, so the Git review form can use any existing local Windows path, for example `D:\python_development\pycharm_develop_space\graduation-design`. Docker no longer mounts a fixed repository directory.

Stop the stack with:

```powershell
docker compose down
```

The script keeps the Java process in the foreground. Press `Ctrl+C` to stop Java, then run `docker compose down` to stop the Docker services. MySQL data is kept in the named `mysql-data` volume. The Docker database uses host port `3307` by default to avoid conflicts with an existing local MySQL installation.

## Run the frontend

Requires Node.js 20+.

```powershell
cd frontend
npm install
npm run dev
```

Open the Vite URL shown in the terminal. Git scanning under `/api/repositories/*` is proxied to Java at `http://localhost:8080`; AI review requests under `/api/ai/*` are proxied to the LangChain service at `http://localhost:8090`. The Git review button sends the selected patch content directly to the LLM service. When a service is unavailable, the page shows the corresponding connection error.

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

`/api/ai/*` is provided by the LangChain service on port `8090`. Java on port `8080` only handles local Git scanning, MySQL persistence, and history queries; it does not call the LLM.
