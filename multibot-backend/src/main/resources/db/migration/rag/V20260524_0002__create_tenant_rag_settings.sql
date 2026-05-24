CREATE TABLE tenant_rag_settings (
    tenant_id      UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    min_score      NUMERIC(3,2)  NOT NULL DEFAULT 0.30,
    top_k          INT           NOT NULL DEFAULT 5,
    temperature    NUMERIC(2,1)  NOT NULL DEFAULT 0.2,
    system_prompt  TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT rag_min_score_range CHECK (min_score >= 0.0 AND min_score <= 1.0),
    CONSTRAINT rag_top_k_range     CHECK (top_k >= 1 AND top_k <= 20),
    CONSTRAINT rag_temp_range      CHECK (temperature >= 0.0 AND temperature <= 1.0)
);

INSERT INTO tenant_rag_settings (tenant_id) VALUES
    ('00000000-0000-7000-8000-000000000001');
