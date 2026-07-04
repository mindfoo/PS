-- ============================================================================
-- Workflow Platform Database Schema
-- Generated from JPA entities
-- PostgreSQL 15+
-- ============================================================================

-- Enable UUID extension (PostgreSQL)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Roles table
CREATE TABLE IF NOT EXISTS roles (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(64) NOT NULL UNIQUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Permission table
CREATE TABLE IF NOT EXISTS permission (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    resource        VARCHAR(64) NOT NULL,  -- WORKFLOW, TASK, USER, SCHEDULE, EXECUTION
    action          VARCHAR(64) NOT NULL,  -- READ, WRITE, DELETE, EXECUTE, MANAGE
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT permission_resource_check CHECK (resource IN ('WORKFLOW', 'TASK', 'USER', 'SCHEDULE', 'EXECUTION')),
    CONSTRAINT permission_action_check CHECK (action IN ('READ', 'WRITE', 'DELETE', 'EXECUTE', 'MANAGE'))
);

CREATE TABLE IF NOT EXISTS role_permission (
    role_id         UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id   UUID NOT NULL REFERENCES permission(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username                VARCHAR(64) NOT NULL UNIQUE,
    password_validation     VARCHAR(256) NOT NULL,
    role_id                 UUID NOT NULL REFERENCES roles(id),
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated            TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    token_hash      VARCHAR(256) NOT NULL UNIQUE,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at      TIMESTAMP NOT NULL
);

-- Workflows table
CREATE TABLE IF NOT EXISTS workflows (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(64) NOT NULL,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    is_private      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tasks table
CREATE TABLE IF NOT EXISTS tasks (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(64) NOT NULL,
    type            VARCHAR(64) NOT NULL,
    config          JSONB NOT NULL DEFAULT '{}'::jsonb,
    workflow_id     UUID REFERENCES workflows(id) ON DELETE SET NULL,
    created_by      UUID REFERENCES users(id) ON DELETE SET NULL,
    is_private      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workflow_tasks_order (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_id         UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    task_id             UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    depends_on_task_id  UUID REFERENCES tasks(id) ON DELETE SET NULL,
    task_order          INTEGER NOT NULL,
    retry_policy        INTEGER NOT NULL DEFAULT 0,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated        TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Executions table (history of workflow/task runs)
CREATE TABLE IF NOT EXISTS executions (
    id                      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    triggered_type          VARCHAR(64) NOT NULL,  -- MANUAL, CRON, EVENT
    type                    VARCHAR(64) NOT NULL,  -- TASK, WORKFLOW
    status                  VARCHAR(64) NOT NULL,  -- PENDING, RUNNING, SUCCESS, ERROR
    output                  JSONB,
    started_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at             TIMESTAMP,
    retry_count             INTEGER NOT NULL DEFAULT 0,
    triggered_by            UUID NOT NULL REFERENCES users(id),
    task_id                 UUID REFERENCES tasks(id) ON DELETE SET NULL,
    workflow_id             UUID REFERENCES workflows(id) ON DELETE SET NULL,
    parent_execution_id     UUID REFERENCES executions(id) ON DELETE SET NULL,
    task_name               VARCHAR(128),
    cancel_requested        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated            TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT executions_type_check CHECK (type IN ('TASK', 'WORKFLOW')),
    CONSTRAINT executions_status_check CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'ERROR')),
    CONSTRAINT executions_triggered_type_check CHECK (triggered_type IN ('MANUAL', 'CRON', 'EVENT'))
);

CREATE TABLE IF NOT EXISTS schedules (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_id     UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    cron_expression VARCHAR(128) NOT NULL,
    timezone        VARCHAR(64) NOT NULL DEFAULT 'UTC',
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    next_run_at     TIMESTAMP NOT NULL,
    last_run_at     TIMESTAMP,
    description     VARCHAR(128),
    created_by      UUID NOT NULL REFERENCES users(id),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
