# Workflow Platform

A workflow orchestration platform. The backend is a Spring Boot REST API that lets users create, manage, and monitor configurable pipelines composed of ordered tasks — with RBAC, execution logging, retry policies and cron scheduling. 
The frontend is a React SPA that consumes the API.

---

## Project Structure

```
PS/
├── package.json          # Root scripts — run/test both modules at once
├── code/
│   ├── jvm/              # Backend — Kotlin + Spring Boot 3 + PostgreSQL
│   └── js/               # Frontend — React 18 + TypeScript + Vite

```
---

## Prerequisites

- **Java 25** (JDK)
- **Node.js 20+** 
- **Docker** 

---

## Running the Project

### Option 1 — Both modules from the root 

Install root dependencies once:

```bash
npm install
```

Then start everything with a single command:

```bash
npm run dev
```

| URL | Description |
|-----|------------|
| `http://localhost:8080` | REST API |
| `http://localhost:5173` | Frontend |

---

### Option 2 — Each module individually

#### Backend

```bash
cd code/jvm
./gradlew dev
```

#### Frontend

```bash
cd code/js
npm install            
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

## Database Schema Management

### 🔄 Automatic (Hibernate DDL - Current Mode)

The application uses **Hibernate's auto-update mode** for development convenience:

```properties
# code/jvm/src/main/resources/application.properties
spring.jpa.hibernate.ddl-auto=update
```

**What this means:**
- ✅ **Tables are created automatically** from `@Entity` classes on first startup
- ✅ **Your data is SAFE** - `update` mode never drops tables or deletes data
- ✅ **Schema changes are applied automatically** when you add new fields to entities
- ✅ **Initial data is seeded** by `DataInitializer.kt` (only on first run)

### 📝 Manual (SQL Scripts - Best Practice)

For production or when you want explicit control, SQL scripts are available in `code/jvm/src/main/resources/db/`:

| Script | Purpose |
|--------|---------|
| `schema.sql` | Complete DDL (tables, indexes, constraints) |
| `data-seed.sql` | Initial data (roles, permissions, admin user) |

#### Execute scripts manually:

```bash
cd code/jvm

# Option 1: Initialize from scratch (schema + seed data)
./gradlew dbInit

# Option 2: Run scripts individually
./gradlew dbSchema    # create tables only
./gradlew dbSeed      # populate initial data only

# Option 3: Using psql directly
docker exec -i workflow-postgres psql -U admin -d workflow_db < src/main/resources/db/schema.sql
docker exec -i workflow-postgres psql -U admin -d workflow_db < src/main/resources/db/data-seed.sql
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

## API Overview

Base URL: `http://localhost:8080/swagger-ui/index.html`

