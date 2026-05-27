-- Konta uzytkownikow panelu admina + super admina platformy.
-- tenant_id nullable: NULL = SUPER_ADMIN (ponad tenantami), inni ADMIN/OWNER zwiazani z tenant.

CREATE TABLE users
(
    id              UUID                     PRIMARY KEY,
    email           VARCHAR(255)             NOT NULL,
    password_hash   VARCHAR(255)             NOT NULL,
    role            VARCHAR(32)              NOT NULL,
    status          VARCHAR(32)              NOT NULL DEFAULT 'ACTIVE',
    tenant_id       UUID,
    created_by      UUID,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at   TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('SUPER_ADMIN', 'OWNER', 'ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'MUST_CHANGE_PASSWORD')),
    CONSTRAINT ck_users_tenant_for_role CHECK (
        (role = 'SUPER_ADMIN' AND tenant_id IS NULL) OR
        (role IN ('OWNER', 'ADMIN') AND tenant_id IS NOT NULL)
    ),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_users_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_users_tenant ON users (tenant_id);
CREATE INDEX idx_users_role ON users (role);
CREATE UNIQUE INDEX idx_users_one_owner_per_tenant
    ON users (tenant_id)
    WHERE role = 'OWNER';
