CREATE TABLE chunks
(
    id          UUID                     PRIMARY KEY,
    tenant_id   UUID                     NOT NULL,
    document_id UUID                     NOT NULL,
    chunk_index INT                      NOT NULL,
    content     TEXT                     NOT NULL,
    embedding   VECTOR(1536)             NOT NULL,
    metadata    JSONB,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_chunks_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT uq_chunks_document_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_chunks_tenant_document ON chunks (tenant_id, document_id);
CREATE INDEX idx_chunks_metadata ON chunks USING GIN (metadata);
