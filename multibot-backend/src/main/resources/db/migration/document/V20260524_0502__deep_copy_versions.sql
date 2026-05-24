-- Zmiana z many-to-many (version_documents) na deep-copy
-- Dokumenty per wersja — każda wersja ma swoje own rows w documents + chunks

DROP TABLE IF EXISTS version_documents CASCADE;

-- Dodaj kolumny version_id
ALTER TABLE documents ADD COLUMN version_id UUID;
ALTER TABLE chunks    ADD COLUMN version_id UUID;

-- Backfill: każdy istniejący document/chunk dostaje version_id z aktywnej wersji tenanta
UPDATE documents d
SET version_id = (SELECT cav.version_id FROM chatbot_active_version cav WHERE cav.tenant_id = d.tenant_id);

UPDATE chunks c
SET version_id = (SELECT cav.version_id FROM chatbot_active_version cav WHERE cav.tenant_id = c.tenant_id);

-- Wymuś NOT NULL + FK
ALTER TABLE documents ALTER COLUMN version_id SET NOT NULL;
ALTER TABLE chunks    ALTER COLUMN version_id SET NOT NULL;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_version
    FOREIGN KEY (version_id) REFERENCES chatbot_versions(id) ON DELETE CASCADE;

ALTER TABLE chunks
    ADD CONSTRAINT fk_chunks_version
    FOREIGN KEY (version_id) REFERENCES chatbot_versions(id) ON DELETE CASCADE;

CREATE INDEX idx_documents_tenant_version ON documents(tenant_id, version_id);
CREATE INDEX idx_chunks_tenant_version    ON chunks(tenant_id, version_id);

-- Unikalność filename per wersja (różne wersje mogą mieć ten sam filename)
CREATE UNIQUE INDEX uq_documents_version_filename ON documents(version_id, filename);
