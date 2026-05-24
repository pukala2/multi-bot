package com.multibot.vector.application;

import com.multibot.botversion.domain.port.in.GetActiveVersionUseCase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import com.multibot.vector.domain.model.Chunk;
import com.multibot.vector.domain.model.ChunkId;
import com.multibot.vector.domain.model.ChunkMetadata;
import com.multibot.vector.domain.model.Embedding;
import com.multibot.vector.domain.model.VectorSearchQuery;
import com.multibot.vector.domain.model.VectorSearchResult;
import com.multibot.vector.domain.port.out.ChunkStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorServiceTest {

    private static final TenantId TENANT_A = TenantId.of(UUID.randomUUID());
    private static final TenantId TENANT_B = TenantId.of(UUID.randomUUID());
    private static final int CANDIDATE_MULTIPLIER = 10;

    private ChunkStore chunkStore;
    private GetActiveVersionUseCase getActiveVersion;
    private VectorService service;
    private UUID activeVersionId;

    @BeforeEach
    void setUp() {
        chunkStore = mock(ChunkStore.class);
        getActiveVersion = mock(GetActiveVersionUseCase.class);
        activeVersionId = UUID.randomUUID();
        when(getActiveVersion.getActiveVersionId()).thenReturn(Optional.of(activeVersionId));
        service = new VectorService(chunkStore,
                new com.multibot.vector.config.VectorProperties(CANDIDATE_MULTIPLIER),
                getActiveVersion);
        TenantContext.set(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void storeAll_delegatesToStore_whenAllChunksMatchCurrentTenant() {
        List<Chunk> chunks = List.of(aChunk(TENANT_A, 0), aChunk(TENANT_A, 1));

        service.storeAll(chunks);

        verify(chunkStore).storeAll(chunks);
    }

    @Test
    void storeAll_throws_whenChunkBelongsToDifferentTenant() {
        List<Chunk> mixed = List.of(aChunk(TENANT_A, 0), aChunk(TENANT_B, 1));

        assertThatThrownBy(() -> service.storeAll(mixed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match current TenantContext");
    }

    @Test
    void search_passesCurrentTenantAndVersionToStore() {
        Embedding query = randomEmbedding();
        when(chunkStore.search(eq(TENANT_A), eq(activeVersionId), eq(query), eq(50)))
                .thenReturn(List.of(aResult(0.9), aResult(0.8)));

        List<VectorSearchResult> results = service.search(new VectorSearchQuery(query, 5, 0.7));

        ArgumentCaptor<TenantId> tenantCaptor = ArgumentCaptor.forClass(TenantId.class);
        ArgumentCaptor<UUID> versionCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(chunkStore).search(tenantCaptor.capture(), versionCaptor.capture(), eq(query), eq(50));
        assertThat(tenantCaptor.getValue()).isEqualTo(TENANT_A);
        assertThat(versionCaptor.getValue()).isEqualTo(activeVersionId);
        assertThat(results).hasSize(2);
    }

    @Test
    void search_appliesMinScoreFilter() {
        Embedding query = randomEmbedding();
        when(chunkStore.search(eq(TENANT_A), eq(activeVersionId), eq(query), eq(50)))
                .thenReturn(List.of(aResult(0.9), aResult(0.6), aResult(0.5)));

        List<VectorSearchResult> results = service.search(new VectorSearchQuery(query, 5, 0.7));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).similarity()).isGreaterThanOrEqualTo(0.7);
    }

    @Test
    void search_appliesTopKLimit() {
        Embedding query = randomEmbedding();
        when(chunkStore.search(eq(TENANT_A), eq(activeVersionId), eq(query), eq(20)))
                .thenReturn(List.of(aResult(0.99), aResult(0.95), aResult(0.90), aResult(0.85)));

        List<VectorSearchResult> results = service.search(new VectorSearchQuery(query, 2, 0.5));

        assertThat(results).hasSize(2);
    }

    @Test
    void search_returnsEmpty_whenNoActiveVersion() {
        when(getActiveVersion.getActiveVersionId()).thenReturn(Optional.empty());

        List<VectorSearchResult> results = service.search(
                new VectorSearchQuery(randomEmbedding(), 5, 0.5));

        assertThat(results).isEmpty();
    }

    @Test
    void deleteByDocument_passesCurrentTenantToStore() {
        UUID documentId = UUID.randomUUID();

        service.deleteByDocument(documentId);

        verify(chunkStore).deleteByDocument(TENANT_A, documentId);
    }

    private Chunk aChunk(TenantId tenant, int index) {
        return new Chunk(
                ChunkId.generate(),
                tenant,
                activeVersionId,
                UUID.randomUUID(),
                index,
                "Chunk content " + index,
                randomEmbedding(),
                ChunkMetadata.EMPTY,
                Instant.now());
    }

    private VectorSearchResult aResult(double similarity) {
        return new VectorSearchResult(aChunk(TENANT_A, 0), similarity);
    }

    private Embedding randomEmbedding() {
        float[] values = new float[Embedding.DIMENSIONS];
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) Math.random();
        }
        return Embedding.of(values);
    }
}
