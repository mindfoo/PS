-- ============================================================================
-- Workflow Platform Initial Data Seed
-- Corresponds to DataInitializer.kt logic
-- PostgreSQL 15+
-- ============================================================================

-- ============================================================================
-- 1. Create Roles
-- ============================================================================

INSERT INTO roles (id, name, created_at, last_updated)
VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000002'::uuid, 'WRITER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000003'::uuid, 'READER', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('00000000-0000-0000-0000-000000000004'::uuid, 'DEV', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (name) DO NOTHING;

-- ============================================================================
-- 2. Create Permissions (resource × action pairs)
-- ============================================================================

-- Workflow permissions
INSERT INTO permission (id, resource, action, created_at, last_updated)
VALUES
    ('10000000-0000-0000-0000-000000000001'::uuid, 'WORKFLOW', 'READ', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000002'::uuid, 'WORKFLOW', 'WRITE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000003'::uuid, 'WORKFLOW', 'DELETE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('10000000-0000-0000-0000-000000000004'::uuid, 'WORKFLOW', 'EXECUTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- Task permissions
INSERT INTO permission (id, resource, action, created_at, last_updated)
VALUES
    ('20000000-0000-0000-0000-000000000001'::uuid, 'TASK', 'READ', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000002'::uuid, 'TASK', 'WRITE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000003'::uuid, 'TASK', 'DELETE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('20000000-0000-0000-0000-000000000004'::uuid, 'TASK', 'EXECUTE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- Schedule permissions
INSERT INTO permission (id, resource, action, created_at, last_updated)
VALUES
    ('30000000-0000-0000-0000-000000000001'::uuid, 'SCHEDULE', 'READ', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-0000-0000-000000000002'::uuid, 'SCHEDULE', 'WRITE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('30000000-0000-0000-0000-000000000003'::uuid, 'SCHEDULE', 'DELETE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- Execution permissions
INSERT INTO permission (id, resource, action, created_at, last_updated)
VALUES
    ('40000000-0000-0000-0000-000000000001'::uuid, 'EXECUTION', 'READ', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- User management permissions
INSERT INTO permission (id, resource, action, created_at, last_updated)
VALUES
    ('50000000-0000-0000-0000-000000000001'::uuid, 'USER', 'MANAGE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 3. Assign Permissions to Roles
-- ============================================================================

-- READER role: read-only access
INSERT INTO role_permission (role_id, permission_id)
VALUES
    ('00000000-0000-0000-0000-000000000003'::uuid, '10000000-0000-0000-0000-000000000001'::uuid), -- workflow:read
    ('00000000-0000-0000-0000-000000000003'::uuid, '20000000-0000-0000-0000-000000000001'::uuid), -- task:read
    ('00000000-0000-0000-0000-000000000003'::uuid, '30000000-0000-0000-0000-000000000001'::uuid), -- schedule:read
    ('00000000-0000-0000-0000-000000000003'::uuid, '40000000-0000-0000-0000-000000000001'::uuid)  -- execution:read
ON CONFLICT DO NOTHING;

-- WRITER role: read + write + delete (no execution)
INSERT INTO role_permission (role_id, permission_id)
VALUES
    ('00000000-0000-0000-0000-000000000002'::uuid, '10000000-0000-0000-0000-000000000001'::uuid), -- workflow:read
    ('00000000-0000-0000-0000-000000000002'::uuid, '10000000-0000-0000-0000-000000000002'::uuid), -- workflow:write
    ('00000000-0000-0000-0000-000000000002'::uuid, '10000000-0000-0000-0000-000000000003'::uuid), -- workflow:delete
    ('00000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000001'::uuid), -- task:read
    ('00000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000002'::uuid), -- task:write
    ('00000000-0000-0000-0000-000000000002'::uuid, '20000000-0000-0000-0000-000000000003'::uuid), -- task:delete
    ('00000000-0000-0000-0000-000000000002'::uuid, '30000000-0000-0000-0000-000000000001'::uuid), -- schedule:read
    ('00000000-0000-0000-0000-000000000002'::uuid, '30000000-0000-0000-0000-000000000002'::uuid), -- schedule:write
    ('00000000-0000-0000-0000-000000000002'::uuid, '30000000-0000-0000-0000-000000000003'::uuid), -- schedule:delete
    ('00000000-0000-0000-0000-000000000002'::uuid, '40000000-0000-0000-0000-000000000001'::uuid)  -- execution:read
ON CONFLICT DO NOTHING;

-- DEV role: read + execute (no write/delete)
INSERT INTO role_permission (role_id, permission_id)
VALUES
    ('00000000-0000-0000-0000-000000000004'::uuid, '10000000-0000-0000-0000-000000000001'::uuid), -- workflow:read
    ('00000000-0000-0000-0000-000000000004'::uuid, '10000000-0000-0000-0000-000000000004'::uuid), -- workflow:execute
    ('00000000-0000-0000-0000-000000000004'::uuid, '20000000-0000-0000-0000-000000000001'::uuid), -- task:read
    ('00000000-0000-0000-0000-000000000004'::uuid, '20000000-0000-0000-0000-000000000004'::uuid), -- task:execute
    ('00000000-0000-0000-0000-000000000004'::uuid, '40000000-0000-0000-0000-000000000001'::uuid)  -- execution:read
ON CONFLICT DO NOTHING;

-- ADMIN role: all permissions
INSERT INTO role_permission (role_id, permission_id)
VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000001'::uuid), -- workflow:read
    ('00000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000002'::uuid), -- workflow:write
    ('00000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000003'::uuid), -- workflow:delete
    ('00000000-0000-0000-0000-000000000001'::uuid, '10000000-0000-0000-0000-000000000004'::uuid), -- workflow:execute
    ('00000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000001'::uuid), -- task:read
    ('00000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000002'::uuid), -- task:write
    ('00000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000003'::uuid), -- task:delete
    ('00000000-0000-0000-0000-000000000001'::uuid, '20000000-0000-0000-0000-000000000004'::uuid), -- task:execute
    ('00000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000001'::uuid), -- schedule:read
    ('00000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000002'::uuid), -- schedule:write
    ('00000000-0000-0000-0000-000000000001'::uuid, '30000000-0000-0000-0000-000000000003'::uuid), -- schedule:delete
    ('00000000-0000-0000-0000-000000000001'::uuid, '40000000-0000-0000-0000-000000000001'::uuid), -- execution:read
    ('00000000-0000-0000-0000-000000000001'::uuid, '50000000-0000-0000-0000-000000000001'::uuid)  -- user:manage
ON CONFLICT DO NOTHING;

-- ============================================================================
-- 4. Create Default Admin User
-- Password: Admin@12345!
-- Hash generated with: BCrypt.with(BCrypt.Version.VERSION_2A).hashToString(10, "Admin@12345!")
-- ============================================================================

INSERT INTO users (id, username, password_validation, role_id, created_at, last_updated)
VALUES (
    '99999999-9999-9999-9999-999999999999'::uuid,
    'admin',
    '$2a$10$Aldh5m.WBsh9lFCaB8sZ6.XMICDa6Ry1Cb9BT4JqcWDxnd6uQVryK',
    '00000000-0000-0000-0000-000000000001'::uuid,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (username) DO NOTHING;



