CREATE TABLE chat_sessions
(
    id                    UUID                     PRIMARY KEY,
    tenant_id             UUID                     NOT NULL,
    end_user_external_id  VARCHAR(255),
    anonymous_id          VARCHAR(255)             NOT NULL,
    started_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    last_activity_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    metadata              JSONB,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_chat_sessions_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT
);

CREATE INDEX idx_chat_sessions_tenant_activity ON chat_sessions (tenant_id, last_activity_at DESC);
CREATE INDEX idx_chat_sessions_tenant_external ON chat_sessions (tenant_id, end_user_external_id)
    WHERE end_user_external_id IS NOT NULL;
CREATE INDEX idx_chat_sessions_anonymous ON chat_sessions (anonymous_id);

CREATE TABLE messages
(
    id          UUID                     PRIMARY KEY,
    tenant_id   UUID                     NOT NULL,
    session_id  UUID                     NOT NULL,
    role        VARCHAR(16)              NOT NULL,
    content     TEXT                     NOT NULL,
    sources     JSONB,
    tokens_used INT,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_messages_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_messages_sessions FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE,
    CONSTRAINT ck_messages_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX idx_messages_session_created ON messages (session_id, created_at);
CREATE INDEX idx_messages_tenant_created ON messages (tenant_id, created_at DESC);
