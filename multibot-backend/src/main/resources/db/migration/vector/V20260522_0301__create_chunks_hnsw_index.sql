CREATE INDEX idx_chunks_embedding
    ON chunks
        USING HNSW (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 128);
