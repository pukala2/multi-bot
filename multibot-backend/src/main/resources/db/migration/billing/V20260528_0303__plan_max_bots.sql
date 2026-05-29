-- Limit liczby botow (stron) per plan. Hamulec na koszt: kazdy bot = osobna baza wiedzy
-- (embeddingi + storage), niezalezny od wspolnego limitu wiadomosci.
-- max_bots NULL = bez limitu (Enterprise — ustalane per organizacja przez override).
ALTER TABLE billing_plans ADD COLUMN max_bots INTEGER;
ALTER TABLE billing_plans ADD CONSTRAINT ck_billing_plans_max_bots
    CHECK (max_bots IS NULL OR max_bots > 0);

UPDATE billing_plans SET max_bots = 1 WHERE code = 'FREE';
UPDATE billing_plans SET max_bots = 2 WHERE code = 'BASIC';
UPDATE billing_plans SET max_bots = 3 WHERE code = 'PRO';
-- ENTERPRISE: max_bots zostaje NULL (do ustalenia per klient przez max_bots_override)

-- Negocjowany limit per organizacja (analogicznie do monthly_messages_limit_override).
ALTER TABLE organizations ADD COLUMN max_bots_override INTEGER;
ALTER TABLE organizations ADD CONSTRAINT ck_organizations_max_bots_override
    CHECK (max_bots_override IS NULL OR max_bots_override > 0);
