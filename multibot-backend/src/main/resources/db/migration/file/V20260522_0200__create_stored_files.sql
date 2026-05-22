CREATE TABLE stored_files
(
    id                UUID                     PRIMARY KEY,
    tenant_id         UUID                     NOT NULL,
    original_filename VARCHAR(512)             NOT NULL,
    mime_type         VARCHAR(128)             NOT NULL,
    size_bytes        BIGINT                   NOT NULL,
    storage_key       VARCHAR(1024)            NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by        UUID,

    CONSTRAINT fk_stored_files_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT ck_stored_files_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_stored_files_tenant_created ON stored_files (tenant_id, created_at DESC);
