CREATE TABLE documents
(
    id             UUID                     PRIMARY KEY,
    tenant_id      UUID                     NOT NULL,
    stored_file_id UUID                     NOT NULL,
    filename       VARCHAR(512)             NOT NULL,
    mime_type      VARCHAR(128)             NOT NULL,
    size_bytes     BIGINT                   NOT NULL,
    status         VARCHAR(32)              NOT NULL,
    uploaded_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    indexed_at     TIMESTAMP WITH TIME ZONE,
    error_message  TEXT,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by     UUID,
    updated_by     UUID,

    CONSTRAINT fk_documents_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_documents_stored_files FOREIGN KEY (stored_file_id) REFERENCES stored_files (id) ON DELETE RESTRICT,
    CONSTRAINT ck_documents_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX idx_documents_tenant_status ON documents (tenant_id, status);
CREATE INDEX idx_documents_tenant_uploaded ON documents (tenant_id, uploaded_at DESC);
CREATE INDEX idx_documents_stored_file ON documents (stored_file_id);
