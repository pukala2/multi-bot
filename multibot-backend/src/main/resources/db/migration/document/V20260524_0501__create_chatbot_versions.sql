CREATE TABLE chatbot_versions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at  TIMESTAMPTZ,
    CONSTRAINT chatbot_versions_status_check CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_chatbot_versions_tenant ON chatbot_versions(tenant_id);

CREATE TABLE version_documents (
    version_id   UUID        NOT NULL REFERENCES chatbot_versions(id) ON DELETE CASCADE,
    document_id  UUID        NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (version_id, document_id)
);

CREATE INDEX idx_version_documents_document ON version_documents(document_id);

CREATE TABLE chatbot_active_version (
    tenant_id   UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    version_id  UUID         NOT NULL REFERENCES chatbot_versions(id) ON DELETE RESTRICT,
    activated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Backfill: dla każdego tenanta z dokumentami stwórz "Wersja 1" jako ACTIVE
-- i przypisz wszystkie istniejące dokumenty
DO $$
DECLARE
    t_row RECORD;
    new_version_id UUID;
BEGIN
    FOR t_row IN
        SELECT DISTINCT tenant_id FROM documents
    LOOP
        new_version_id := gen_random_uuid();

        INSERT INTO chatbot_versions (id, tenant_id, name, status, published_at)
        VALUES (new_version_id, t_row.tenant_id, 'Wersja 1', 'ACTIVE', NOW());

        INSERT INTO version_documents (version_id, document_id)
        SELECT new_version_id, d.id
        FROM documents d
        WHERE d.tenant_id = t_row.tenant_id;

        INSERT INTO chatbot_active_version (tenant_id, version_id)
        VALUES (t_row.tenant_id, new_version_id);
    END LOOP;
END $$;

-- Dla tenantów bez dokumentów też tworzymy pustą Wersja 1 (gdyby się wgrywali w przyszlosci)
DO $$
DECLARE
    t_row RECORD;
    new_version_id UUID;
BEGIN
    FOR t_row IN
        SELECT t.id FROM tenants t
        WHERE NOT EXISTS (SELECT 1 FROM chatbot_active_version WHERE tenant_id = t.id)
    LOOP
        new_version_id := gen_random_uuid();

        INSERT INTO chatbot_versions (id, tenant_id, name, status, published_at)
        VALUES (new_version_id, t_row.id, 'Wersja 1', 'ACTIVE', NOW());

        INSERT INTO chatbot_active_version (tenant_id, version_id)
        VALUES (t_row.id, new_version_id);
    END LOOP;
END $$;

-- Constraint na pojedynczy ACTIVE per tenant
CREATE UNIQUE INDEX idx_chatbot_versions_one_active_per_tenant
    ON chatbot_versions(tenant_id)
    WHERE status = 'ACTIVE';
