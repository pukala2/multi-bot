-- Portfel AI (wewnętrzny, tylko dla super admina): rejestr doładowań środków na tokeny.
-- Pozostało = SUMA(amount_usd) − SUMA(tenant_usage.cost_usd) liczone od początku (all-time).
-- amount_usd może być ujemny = korekta (np. gdy faktura dostawcy różni się od naszego szacunku).
CREATE TABLE ai_credit_topups
(
    id          UUID                     PRIMARY KEY,
    amount_usd  NUMERIC(10, 2)           NOT NULL,
    note        VARCHAR(255),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    created_by  UUID
);

CREATE INDEX idx_ai_credit_topups_created ON ai_credit_topups (created_at DESC);
