CREATE TABLE widget_settings (
    tenant_id     UUID PRIMARY KEY REFERENCES tenants(id) ON DELETE CASCADE,
    accent_color  VARCHAR(20) NOT NULL DEFAULT '#7B5BFF',
    position      VARCHAR(20) NOT NULL DEFAULT 'bottom-right',
    title         VARCHAR(60) NOT NULL DEFAULT 'Asystent',
    greeting      TEXT        NOT NULL DEFAULT 'Cześć! Jak mogę pomóc?',
    placeholder   VARCHAR(80) NOT NULL DEFAULT 'Napisz wiadomość…',
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT widget_settings_position_check CHECK (position IN ('bottom-right', 'bottom-left'))
);

CREATE TABLE widget_keys (
    key           VARCHAR(64) PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at  TIMESTAMPTZ
);

CREATE INDEX idx_widget_keys_tenant ON widget_keys(tenant_id);

INSERT INTO widget_settings (tenant_id, accent_color, title, greeting, placeholder)
VALUES (
    '00000000-0000-7000-8000-000000000001',
    '#7B5BFF',
    'Asystent',
    'Cześć! Jak mogę pomóc?',
    'Napisz wiadomość…'
);

INSERT INTO widget_keys (key, tenant_id)
VALUES (
    'pk_widget_demo_default_x9k2',
    '00000000-0000-7000-8000-000000000001'
);
