-- User nalezy do ORGANIZACJI (klienta), nie do pojedynczego bota.
-- Dzieki temu jeden owner zarzadza wszystkimi botami swojej organizacji.
ALTER TABLE users ADD COLUMN organization_id UUID;

-- org.id == stary tenant.id (patrz V..0300), wiec organization_id = dotychczasowy tenant_id.
UPDATE users SET organization_id = tenant_id WHERE tenant_id IS NOT NULL;

-- Usun stare powiazania z tenant.
DROP INDEX IF EXISTS idx_users_one_owner_per_tenant;
DROP INDEX IF EXISTS idx_users_tenant;
ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_tenant_for_role;
ALTER TABLE users DROP CONSTRAINT IF EXISTS fk_users_tenant;
ALTER TABLE users DROP COLUMN tenant_id;

-- Nowe powiazanie z organizacja. SUPER_ADMIN nadal bez organizacji (ponad klientami).
ALTER TABLE users ADD CONSTRAINT fk_users_organization
    FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE;
ALTER TABLE users ADD CONSTRAINT ck_users_org_for_role CHECK (
    (role = 'SUPER_ADMIN' AND organization_id IS NULL) OR
    (role IN ('OWNER', 'ADMIN') AND organization_id IS NOT NULL)
);
CREATE INDEX idx_users_organization ON users (organization_id);
CREATE UNIQUE INDEX idx_users_one_owner_per_org
    ON users (organization_id)
    WHERE role = 'OWNER';
