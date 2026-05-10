# Workflow Platform

A full-stack workflow orchestration platform. The backend is a Spring Boot 3 REST API that lets users create, manage, and monitor configurable pipelines composed of ordered tasks — with RBAC, execution logging, retry policies, alerting, and cron scheduling. The frontend is a React SPA that consumes the API.

---

## Project Structure

```
PS/
├── package.json          # Root scripts — run/test both modules at once
├── code/
│   ├── jvm/              # Backend — Kotlin + Spring Boot 3 + PostgreSQL
│   │   ├── build.gradle.kts
│   │   ├── gradlew
│   │   └── src/
│   │       ├── docker-compose.yml   # PostgreSQL 15 container
│   │       └── main/kotlin/workflow/
│   │           ├── controller/
│   │           ├── service/
│   │           ├── repository/
│   │           ├── entity/
│   │           ├── dto/
│   │           └── security/
│   └── js/               # Frontend — React 18 + TypeScript + Vite
│       ├── package.json
│       └── src/
│           ├── api/
│           ├── pages/
│           ├── components/
│           └── contexts/
└── docs/
    └── DOC.md
```

### Tech Stack

| Layer     | Technology                                      |
|-----------|-------------------------------------------------|
| Backend   | Kotlin, Spring Boot 3.5, Spring Security, Spring Data JPA |
| Database  | PostgreSQL 15 (Docker)                          |
| Frontend  | React 18, TypeScript, Vite 6, React Router 6    |
| Auth      | Cookie-based opaque tokens                      |
| Build     | Gradle (Kotlin DSL), npm                        |
| Tests     | JUnit 5, MockK, JaCoCo · Vitest, Testing Library, Playwright |

---

## Prerequisites

- **Java 25** (JDK)
- **Node.js 20+** and **npm**
- **Docker Desktop** (must be running before starting the backend)

### Check Docker is running

```bash
docker info
```

---

## Running the Project

### Option 1 — Both modules from the root (recommended)

Install root dependencies once:

```bash
npm install
```

Then start everything with a single command:

```bash
npm run dev
```

This runs `./gradlew dev` (backend) and `npm run dev` (frontend) in parallel via `concurrently`.  
Output is colour-coded: **cyan = JVM**, **magenta = JS**.

| URL | Description |
|-----|-------------|
| `http://localhost:8080` | REST API |
| `http://localhost:5173` | Frontend (proxies `/api` → `8080`) |

---

### Option 2 — Each module individually

#### Backend

```bash
cd code/jvm

# 1. Start PostgreSQL and wait until healthy, then boot Spring Boot
./gradlew dev

# — or run each step manually —
./gradlew dbUp          # start the DB container
./gradlew bootRun       # start Spring Boot (assumes DB is already up)
```

#### Frontend

```bash
cd code/js
npm install             # first time only
npm run dev             # starts Vite dev server on http://localhost:5173
```

---


> If you need to reset the database to a clean state:
> ```bash
> cd code/jvm
> ./gradlew dbReset   # stops container AND deletes the postgres_data volume (destructive)
> ./gradlew dev       # recreates everything from scratch
> ```

---

## Useful DB Commands

All run from `code/jvm/`:

```bash
./gradlew dbUp      # start the PostgreSQL container
./gradlew dbDown    # stop the container (data is preserved in the volume)
./gradlew dbReset   # stop + delete the volume (all data is lost)
./gradlew dbLogs    # tail the PostgreSQL container logs
```

Connect directly with psql:

```bash
docker exec -it workflow-postgres psql -U admin -d workflow_db
```

---

## Running Tests

### All tests (backend + frontend) from the root

```bash
npm test
```

### Backend only

```bash
cd code/jvm
./gradlew test
```

### Frontend only (Vitest unit tests)

```bash
cd code/js
npm test
```


### End-to-end tests (Playwright — requires the app to be running)

```bash
cd code/js
npm run test:e2e
```

---

## Coverage

### Backend (JaCoCo — 80% line coverage gate)

```bash
cd code/jvm
./gradlew test jacocoTestReport
```

HTML report: `code/jvm/build/reports/jacoco/test/html/index.html`

> The build fails if line coverage drops below 80%. The gate is enforced by `jacocoTestCoverageVerification`.

### Frontend (Vitest + v8 — 80% line threshold)

```bash
cd code/js
npm run test:coverage
```

HTML report: `code/js/coverage/index.html`

### Both together from the root

```bash
npm run test:coverage
```

---

## Troubleshooting

### Port 8080 already in use

Find and kill whatever is holding the port:

```bash
# show the process
lsof -i tcp:8080

# kill it
kill -9 $(lsof -ti tcp:8080)
```

### Port 5173 already in use (frontend)

```bash
kill -9 $(lsof -ti tcp:5173)
```

---

## API Overview

Base URL: `http://localhost:8080/swagger-ui/index.html`

