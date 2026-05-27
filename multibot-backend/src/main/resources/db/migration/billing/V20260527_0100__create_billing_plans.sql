-- Definicje planow cenowych. Edytowalne przez super admina (w przyszlosci) lub przez psql.
-- monthly_messages_limit = -1 oznacza unlimited (Enterprise).

CREATE TABLE billing_plans
(
    code                    VARCHAR(32)              PRIMARY KEY,
    name                    VARCHAR(128)             NOT NULL,
    monthly_messages_limit  INTEGER                  NOT NULL,
    custom_domain_allowed   BOOLEAN                  NOT NULL DEFAULT FALSE,
    api_access_allowed      BOOLEAN                  NOT NULL DEFAULT FALSE,
    price_pln_monthly       INTEGER,                 -- null = custom pricing (Enterprise)
    display_order           INTEGER                  NOT NULL DEFAULT 0,
    active                  BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    CONSTRAINT ck_billing_plans_limit_nonzero CHECK (monthly_messages_limit = -1 OR monthly_messages_limit > 0),
    CONSTRAINT ck_billing_plans_price_nonneg CHECK (price_pln_monthly IS NULL OR price_pln_monthly >= 0)
);

-- Seed startowych planow. Pozniej super admin moze zmieniac limity przez UI.
INSERT INTO billing_plans (code, name, monthly_messages_limit, custom_domain_allowed, api_access_allowed, price_pln_monthly, display_order) VALUES
    ('FREE',       'Free',       500,    FALSE, FALSE, 0,    1),
    ('BASIC',      'Basic',      2000,   FALSE, TRUE,  149,  2),
    ('PRO',        'Pro',        10000,  TRUE,  TRUE,  499,  3),
    ('ENTERPRISE', 'Enterprise', -1,     TRUE,  TRUE,  NULL, 4);

-- Konwersja tenants.plan: drop CHECK, dodaj FK do billing_plans.code
ALTER TABLE tenants DROP CONSTRAINT IF EXISTS ck_tenants_plan;
ALTER TABLE tenants ADD CONSTRAINT fk_tenants_plan
    FOREIGN KEY (plan) REFERENCES billing_plans(code);
