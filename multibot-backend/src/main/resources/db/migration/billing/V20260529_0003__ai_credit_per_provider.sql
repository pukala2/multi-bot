-- Portfel AI per dostawca + osobne liczenie kosztu embeddingów (OpenAI).
-- Doładowania dostają dostawcę. Istniejące wpisy -> ANTHROPIC (czat).
ALTER TABLE ai_credit_topups ADD COLUMN provider VARCHAR(16) NOT NULL DEFAULT 'ANTHROPIC';
ALTER TABLE ai_credit_topups ADD CONSTRAINT ck_ai_topups_provider CHECK (provider IN ('OPENAI', 'ANTHROPIC'));

-- tenant_usage: cost_usd / tokens_used = czat (Anthropic). Embeddingi (OpenAI) osobno.
ALTER TABLE tenant_usage ADD COLUMN embedding_tokens BIGINT         NOT NULL DEFAULT 0;
ALTER TABLE tenant_usage ADD COLUMN embedding_cost   NUMERIC(12, 6) NOT NULL DEFAULT 0;
