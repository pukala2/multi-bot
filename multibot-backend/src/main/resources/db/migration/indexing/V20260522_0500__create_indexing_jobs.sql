CREATE TABLE indexing_jobs
(
    id              UUID                     PRIMARY KEY,
    tenant_id       UUID                     NOT NULL,
    document_id     UUID                     NOT NULL,
    status          VARCHAR(32)              NOT NULL,
    error_message   TEXT,
    chunks_created  INT,
    started_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_indexing_jobs_tenants FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE RESTRICT,
    CONSTRAINT fk_indexing_jobs_documents FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT ck_indexing_jobs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_indexing_jobs_document ON indexing_jobs (document_id);
CREATE INDEX idx_indexing_jobs_tenant_status ON indexing_jobs (tenant_id, status);
