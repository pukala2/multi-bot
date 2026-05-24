package com.multibot.vector;

import com.multibot.integration.IntegrationTestBase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import com.multibot.vector.domain.model.Chunk;
import com.multibot.vector.domain.model.ChunkId;
import com.multibot.vector.domain.model.ChunkMetadata;
import com.multibot.vector.domain.model.Embedding;
import com.multibot.vector.domain.model.VectorSearchQuery;
import com.multibot.vector.domain.model.VectorSearchResult;
import com.multibot.vector.domain.port.in.DeleteChunksByDocumentUseCase;
import com.multibot.vector.domain.port.in.SearchChunksUseCase;
import com.multibot.vector.domain.port.in.StoreChunksUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VectorPersistenceIT extends IntegrationTestBase {

    private static final TenantId DEFAULT_TENANT = TenantId.of(
            UUID.fromString("00000000-0000-7000-8000-000000000001"));

    @Autowired
    StoreChunksUseCase storeChunksUseCase;

    @Autowired
    SearchChunksUseCase searchChunksUseCase;

    @Autowired
    DeleteChunksByDocumentUseCase deleteChunksUseCase;

    @Autowired
    JdbcTemplate jdbc;

    private UUID activeVersionId;

    @BeforeEach
    void setUpTenant() {
        TenantContext.set(DEFAULT_TENANT);
        jdbc.update("DELETE FROM chunks");
        jdbc.update("DELETE FROM documents");
        jdbc.update("DELETE FROM stored_files");
        activeVersionId = jdbc.queryForObject(
                "SELECT version_id FROM chatbot_active_version WHERE tenant_id = ?",
                UUID.class, DEFAULT_TENANT.value());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("storeAll persists chunks and search returns closest matches by cosine similarity")
    void shouldStoreAndSearchChunks() {
        UUID documentId = createDummyDocument(DEFAULT_TENANT);
        Embedding queryEmbedding = unitVector(0);

        List<Chunk> chunks = List.of(
                chunk(documentId, 0, "very close match", similarVector(queryEmbedding, 0.99f)),
                chunk(documentId, 1, "medium match", similarVector(queryEmbedding, 0.7f)),
                chunk(documentId, 2, "weak match", similarVector(queryEmbedding, 0.3f))
        );
        storeChunksUseCase.storeAll(chunks);

        List<VectorSearchResult> results = searchChunksUseCase.search(
                new VectorSearchQuery(queryEmbedding, 2, 0.5));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).chunk().content()).isEqualTo("very close match");
        assertThat(results.get(0).similarity()).isGreaterThan(results.get(1).similarity());
    }

    @Test
    @DisplayName("Search respects min score - returns nothing when all candidates below threshold")
    void shouldFilterByMinScore() {
        UUID documentId = createDummyDocument(DEFAULT_TENANT);
        Embedding query = unitVector(0);
        storeChunksUseCase.storeAll(List.of(
                chunk(documentId, 0, "low match", similarVector(query, 0.2f))));

        List<VectorSearchResult> results = searchChunksUseCase.search(
                new VectorSearchQuery(query, 5, 0.9));

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("deleteByDocument removes all chunks of a document")
    void shouldDeleteChunksByDocument() {
        UUID docA = createDummyDocument(DEFAULT_TENANT);
        UUID docB = createDummyDocument(DEFAULT_TENANT);
        Embedding query = unitVector(0);
        storeChunksUseCase.storeAll(List.of(
                chunk(docA, 0, "A1", similarVector(query, 0.9f)),
                chunk(docA, 1, "A2", similarVector(query, 0.8f)),
                chunk(docB, 0, "B1", similarVector(query, 0.7f))));

        deleteChunksUseCase.deleteByDocument(docA);

        List<VectorSearchResult> remaining = searchChunksUseCase.search(
                new VectorSearchQuery(query, 10, 0.0));
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).chunk().content()).isEqualTo("B1");
    }

    @Test
    @DisplayName("Search filters by tenant_id - tenant A cannot see chunks of tenant B")
    void shouldEnforceMultiTenancy() {
        TenantId tenantB = TenantId.of(UUID.fromString("00000000-0000-7000-8000-000000000999"));
        jdbc.update("""
                INSERT INTO tenants (id, name, slug, plan, status, created_at, updated_at)
                VALUES (?, ?, ?, 'FREE', 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """, tenantB.value(), "Tenant B", "tenant-b");
        // Stwórz Wersja 1 ACTIVE dla tenantB (backfill z migracji nie objął tenant utworzonych w teście)
        UUID tenantBVersionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO chatbot_versions (id, tenant_id, name, status, published_at)
                VALUES (?, ?, 'Wersja 1', 'ACTIVE', NOW())
                """, tenantBVersionId, tenantB.value());
        jdbc.update("""
                INSERT INTO chatbot_active_version (tenant_id, version_id) VALUES (?, ?)
                ON CONFLICT (tenant_id) DO UPDATE SET version_id = EXCLUDED.version_id
                """, tenantB.value(), tenantBVersionId);

        UUID documentId = createDummyDocument(tenantB);
        Embedding query = unitVector(0);
        Embedding closeMatch = similarVector(query, 0.99f);

        TenantContext.set(tenantB);
        UUID savedActive = activeVersionId;
        activeVersionId = tenantBVersionId;
        storeChunksUseCase.storeAll(List.of(
                chunk(documentId, 0, "tenant B secret chunk", closeMatch)));
        activeVersionId = savedActive;

        TenantContext.set(DEFAULT_TENANT);
        List<VectorSearchResult> resultsForA = searchChunksUseCase.search(
                new VectorSearchQuery(query, 5, 0.0));

        assertThat(resultsForA).isEmpty();

        TenantContext.set(tenantB);
        List<VectorSearchResult> resultsForB = searchChunksUseCase.search(
                new VectorSearchQuery(query, 5, 0.0));
        assertThat(resultsForB).hasSize(1);
        assertThat(resultsForB.get(0).chunk().content()).isEqualTo("tenant B secret chunk");
    }

    @Test
    @DisplayName("Chunk metadata is persisted and returned as JSON")
    void shouldPersistChunkMetadata() {
        UUID documentId = createDummyDocument(DEFAULT_TENANT);
        Embedding query = unitVector(0);
        Chunk chunk = new Chunk(
                ChunkId.generate(),
                DEFAULT_TENANT,
                activeVersionId,
                documentId,
                0,
                "with metadata",
                similarVector(query, 0.9f),
                ChunkMetadata.of(Map.of("pageNumber", 3, "section", "Wstęp")),
                Instant.now());
        storeChunksUseCase.storeAll(List.of(chunk));

        List<VectorSearchResult> results = searchChunksUseCase.search(
                new VectorSearchQuery(query, 1, 0.0));

        assertThat(results).hasSize(1);
        Map<String, Object> metadata = results.get(0).chunk().metadata().values();
        assertThat(metadata).containsEntry("pageNumber", 3);
        assertThat(metadata).containsEntry("section", "Wstęp");
    }

    private Chunk chunk(UUID documentId, int chunkIndex, String content, Embedding embedding) {
        return new Chunk(
                ChunkId.generate(),
                TenantContext.get(),
                activeVersionId,
                documentId,
                chunkIndex,
                content,
                embedding,
                ChunkMetadata.EMPTY,
                Instant.now());
    }

    /**
     * Creates a normalized unit vector with only one non-zero component (at position).
     * Useful for predictable cosine similarity tests.
     */
    private Embedding unitVector(int position) {
        float[] values = new float[Embedding.DIMENSIONS];
        values[position] = 1.0f;
        return Embedding.of(values);
    }

    /**
     * Creates an embedding with cosine similarity ~= target to the base vector.
     * base must be a unit vector (one position = 1.0, rest = 0).
     */
    private Embedding similarVector(Embedding base, float targetSimilarity) {
        float[] result = new float[Embedding.DIMENSIONS];
        int basePos = -1;
        for (int i = 0; i < base.values().length; i++) {
            if (base.values()[i] != 0.0f) {
                basePos = i;
                break;
            }
        }
        result[basePos] = targetSimilarity;
        float perpComponent = (float) Math.sqrt(Math.max(0, 1 - targetSimilarity * targetSimilarity));
        int perpPos = basePos == 0 ? 1 : 0;
        result[perpPos] = perpComponent;
        return Embedding.of(result);
    }

    private UUID createDummyDocument(TenantId tenant) {
        UUID storedFileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID versionId = jdbc.queryForObject(
                "SELECT version_id FROM chatbot_active_version WHERE tenant_id = ?",
                UUID.class, tenant.value());
        jdbc.update("""
                INSERT INTO stored_files (id, tenant_id, original_filename, mime_type, size_bytes, storage_key, created_at)
                VALUES (?, ?, 'test.pdf', 'application/pdf', 100, ?, NOW())
                """, storedFileId, tenant.value(),
                "tenants/" + tenant.value() + "/files/" + storedFileId + ".pdf");
        jdbc.update("""
                INSERT INTO documents (id, tenant_id, version_id, stored_file_id, filename, mime_type, size_bytes,
                                       status, uploaded_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'test.pdf', 'application/pdf', 100, 'PENDING', NOW(), NOW(), NOW())
                """, documentId, tenant.value(), versionId, storedFileId);
        return documentId;
    }
}
