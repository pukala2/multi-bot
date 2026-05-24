package com.multibot.vector.infrastructure.pgvector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.multibot.shared.tenant.TenantId;
import com.multibot.vector.domain.model.Chunk;
import com.multibot.vector.domain.model.ChunkId;
import com.multibot.vector.domain.model.ChunkMetadata;
import com.multibot.vector.domain.model.Embedding;
import com.multibot.vector.domain.model.VectorSearchResult;
import com.multibot.vector.domain.port.out.ChunkStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

@Component
class PgVectorChunkStoreAdapter implements ChunkStore {

    private static final String INSERT_SQL = """
            INSERT INTO chunks (id, tenant_id, version_id, document_id, chunk_index, content, embedding, metadata, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::vector, ?::jsonb, ?)
            """;

    private static final String SEARCH_SQL = """
            SELECT id, tenant_id, version_id, document_id, chunk_index, content, embedding, metadata, created_at,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM chunks
            WHERE tenant_id = ? AND version_id = ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private static final String DELETE_SQL = """
            DELETE FROM chunks
            WHERE tenant_id = ? AND document_id = ?
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    PgVectorChunkStoreAdapter(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void storeAll(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT_SQL, chunks, chunks.size(), (ps, chunk) -> {
            ps.setObject(1, chunk.id().value());
            ps.setObject(2, chunk.tenantId().value());
            ps.setObject(3, chunk.versionId());
            ps.setObject(4, chunk.documentId());
            ps.setInt(5, chunk.chunkIndex());
            ps.setString(6, chunk.content());
            ps.setString(7, toVectorLiteral(chunk.embedding()));
            ps.setString(8, toJsonString(chunk.metadata()));
            ps.setTimestamp(9, Timestamp.from(chunk.createdAt()));
        });
    }

    @Override
    public List<VectorSearchResult> search(TenantId tenantId, UUID versionId, Embedding queryEmbedding, int candidateLimit) {
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        return jdbc.query(SEARCH_SQL,
                searchRowMapper(),
                vectorLiteral,
                tenantId.value(),
                versionId,
                vectorLiteral,
                candidateLimit);
    }

    @Override
    public void deleteByDocument(TenantId tenantId, UUID documentId) {
        jdbc.update(DELETE_SQL, tenantId.value(), documentId);
    }

    @Override
    public void copyChunks(UUID fromVersionId, UUID toVersionId, Map<UUID, UUID> documentIdMapping) {
        if (documentIdMapping.isEmpty()) return;
        for (Map.Entry<UUID, UUID> entry : documentIdMapping.entrySet()) {
            UUID oldDocId = entry.getKey();
            UUID newDocId = entry.getValue();
            jdbc.update("""
                    INSERT INTO chunks (id, tenant_id, version_id, document_id, chunk_index, content, embedding, metadata, created_at)
                    SELECT gen_random_uuid(), tenant_id, ?, ?, chunk_index, content, embedding, metadata, NOW()
                    FROM chunks
                    WHERE document_id = ? AND version_id = ?
                    """, toVersionId, newDocId, oldDocId, fromVersionId);
        }
    }

    private RowMapper<VectorSearchResult> searchRowMapper() {
        return (rs, rowNum) -> {
            Chunk chunk = new Chunk(
                    ChunkId.of(rs.getObject("id", UUID.class)),
                    TenantId.of(rs.getObject("tenant_id", UUID.class)),
                    rs.getObject("version_id", UUID.class),
                    rs.getObject("document_id", UUID.class),
                    rs.getInt("chunk_index"),
                    rs.getString("content"),
                    Embedding.of(parseVectorLiteral(rs.getString("embedding"))),
                    fromJsonString(rs.getString("metadata")),
                    rs.getTimestamp("created_at").toInstant());
            return new VectorSearchResult(chunk, clamp(rs.getDouble("similarity")));
        };
    }

    private static String toVectorLiteral(Embedding embedding) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (float value : embedding.values()) {
            joiner.add(Float.toString(value));
        }
        return joiner.toString();
    }

    private static float[] parseVectorLiteral(String literal) {
        String stripped = literal.substring(1, literal.length() - 1);
        String[] parts = stripped.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }

    private String toJsonString(ChunkMetadata metadata) {
        if (metadata == null || metadata.values().isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(metadata.values());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chunk metadata", e);
        }
    }

    private ChunkMetadata fromJsonString(String json) {
        if (json == null || json.isBlank()) {
            return ChunkMetadata.EMPTY;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            return ChunkMetadata.of(map);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize chunk metadata", e);
        }
    }

    private static double clamp(double value) {
        if (value < 0.0) return 0.0;
        return Math.min(value, 1.0);
    }
}
