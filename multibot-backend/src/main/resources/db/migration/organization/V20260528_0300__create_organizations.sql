-- Organizacja = klient (konto). Stoi PONAD tenantem (= bot).
-- Plan, limity i dane firmowe sa na poziomie organizacji i wspolne dla wszystkich botow klienta.
CREATE TABLE organizations
(
    id                              UUID                     PRIMARY KEY,
    name                            VARCHAR(255)             NOT NULL,
    slug                            VARCHAR(64)              NOT NULL,
    plan                            VARCHAR(32)              NOT NULL DEFAULT 'FREE',
    status                          VARCHAR(32)              NOT NULL DEFAULT 'ACTIVE',
    company_name                    VARCHAR(255),
    nip                             VARCHAR(20),
    monthly_messages_limit_override INTEGER,
    created_at                      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at                      TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT uq_organizations_slug UNIQUE (slug),
    CONSTRAINT fk_organizations_plan FOREIGN KEY (plan) REFERENCES billing_plans(code),
    CONSTRAINT ck_organizations_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_organizations_override_nonneg
        CHECK (monthly_messages_limit_override IS NULL
               OR monthly_messages_limit_override = -1
               OR monthly_messages_limit_override > 0)
);

CREATE INDEX idx_organizations_status ON organizations (status);

-- Backfill: kazdy istniejacy tenant -> wlasna organizacja 1:1.
-- Reuse UUID tenanta jako id organizacji -> trywialne mapowanie (organization_id = stary tenant_id).
INSERT INTO organizations (id, name, slug, plan, status, company_name, nip,
                           monthly_messages_limit_override, created_at, updated_at)
SELECT id, name, slug, plan, status, company_name, nip,
       monthly_messages_limit_override, created_at, updated_at
FROM tenants;

-- tenants = bot. Dowiazanie do organizacji.
ALTER TABLE tenants ADD COLUMN organization_id UUID;
UPDATE tenants SET organization_id = id;
ALTER TABLE tenants ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_organization
    FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE;
CREATE INDEX idx_tenants_organization ON tenants (organization_id);
