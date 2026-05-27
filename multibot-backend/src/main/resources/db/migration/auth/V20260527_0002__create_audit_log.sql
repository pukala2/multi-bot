-- Audit log dla akcji wrazliwych: impersonacja, tworzenie tenanta, zmiana roli, disable user.
-- Faza A tworzy schemat, faza C wlasciwie z niego korzysta (impersonacja).

CREATE TABLE audit_log
(
    id                UUID                     PRIMARY KEY,
    actor_user_id     UUID                     NOT NULL,
    action            VARCHAR(64)              NOT NULL,
    target_tenant_id  UUID,
    target_user_id    UUID,
    metadata          JSONB,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_tenant FOREIGN KEY (target_tenant_id) REFERENCES tenants(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_user FOREIGN KEY (target_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_actor ON audit_log (actor_user_id, created_at DESC);
CREATE INDEX idx_audit_tenant ON audit_log (target_tenant_id, created_at DESC);
CREATE INDEX idx_audit_created_at ON audit_log (created_at DESC);
