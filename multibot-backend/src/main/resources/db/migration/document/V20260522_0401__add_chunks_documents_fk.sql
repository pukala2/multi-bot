ALTER TABLE chunks
    ADD CONSTRAINT fk_chunks_documents
        FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE;
