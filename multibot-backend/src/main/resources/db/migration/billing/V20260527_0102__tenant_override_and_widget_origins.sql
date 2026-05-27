-- Per-tenant override limitu wiadomosci (np. klient PRO dostal extra 5k jako negocjowany bonus).
-- NULL = uzywaj limitu z planu, INT = uzywaj override zamiast planu.
ALTER TABLE tenants ADD COLUMN monthly_messages_limit_override INTEGER;
ALTER TABLE tenants ADD CONSTRAINT ck_tenants_override_nonneg
    CHECK (monthly_messages_limit_override IS NULL OR monthly_messages_limit_override = -1 OR monthly_messages_limit_override > 0);

-- Allowed origins per widget key (CORS dynamic + Origin check).
-- Pusta tablica = key dziala z kazdej domeny (dev/test). Niepusta = filter.
ALTER TABLE widget_keys ADD COLUMN allowed_origins TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[];

-- Indeksy dla typowych operacji
CREATE INDEX idx_widget_keys_active ON widget_keys (key) WHERE is_active = TRUE;
