-- Counter wykorzystania per tenant per miesiac.
-- period_month = pierwszy dzien miesiaca (np. 2026-05-01), wiec PK (tenant_id, period_month) daje atomic UPSERT.
-- user_messages liczone do limitu planu. ai_messages, tokens_used, cost_usd informacyjnie / dla super admina.

CREATE TABLE tenant_usage
(
    tenant_id      UUID                     NOT NULL,
    period_month   DATE                     NOT NULL,
    user_messages  BIGINT                   NOT NULL DEFAULT 0,
    ai_messages    BIGINT                   NOT NULL DEFAULT 0,
    tokens_used    BIGINT                   NOT NULL DEFAULT 0,
    cost_usd       NUMERIC(10, 4)           NOT NULL DEFAULT 0,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, period_month),
    FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,

    CONSTRAINT ck_tenant_usage_first_of_month CHECK (EXTRACT(DAY FROM period_month) = 1)
);

CREATE INDEX idx_tenant_usage_period ON tenant_usage (period_month);
