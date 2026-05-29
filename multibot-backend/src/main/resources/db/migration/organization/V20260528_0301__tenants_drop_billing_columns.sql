-- Plan, dane firmowe i override przeniesione do organizations.
-- Bot (tenant) trzyma juz tylko: name, slug, status, organization_id.
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS fk_tenants_plan;
ALTER TABLE tenants DROP COLUMN plan;
ALTER TABLE tenants DROP COLUMN company_name;
ALTER TABLE tenants DROP COLUMN nip;
ALTER TABLE tenants DROP COLUMN monthly_messages_limit_override;
