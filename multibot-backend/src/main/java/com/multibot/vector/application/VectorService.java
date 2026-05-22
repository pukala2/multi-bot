package com.multibot.vector.application;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import com.multibot.vector.domain.model.Chunk;
import com.multibot.vector.domain.model.VectorSearchQuery;
import com.multibot.vector.domain.model.VectorSearchResult;
import com.multibot.vector.domain.port.in.DeleteChunksByDocumentUseCase;
import com.multibot.vector.domain.port.in.SearchChunksUseCase;
import com.multibot.vector.domain.port.in.StoreChunksUseCase;
import com.multibot.vector.domain.port.out.ChunkStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
class VectorService implements StoreChunksUseCase, SearchChunksUseCase, DeleteChunksByDocumentUseCase {

    private final ChunkStore chunkStore;
    private final int candidateMultiplier;

    VectorService(ChunkStore chunkStore,
                  @Value("${multibot.rag.candidate-multiplier}") int candidateMultiplier) {
        this.chunkStore = chunkStore;
        this.candidateMultiplier = candidateMultiplier;
    }

    @Override
    @Transactional
    public void storeAll(List<Chunk> chunks) {
        TenantId expected = TenantContext.get();
        chunks.forEach(chunk -> {
            if (!chunk.tenantId().equals(expected)) {
                throw new IllegalStateException(
                        "Chunk tenant_id=%s does not match current TenantContext=%s".formatted(
                                chunk.tenantId(), expected));
            }
        });
        chunkStore.storeAll(chunks);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VectorSearchResult> search(VectorSearchQuery query) {
        TenantId tenant = TenantContext.get();
        int candidateLimit = query.topK() * candidateMultiplier;

        List<VectorSearchResult> candidates = chunkStore.search(tenant, query.queryEmbedding(), candidateLimit);

        return candidates.stream()
                .filter(r -> r.similarity() >= query.minScore())
                .limit(query.topK())
                .toList();
    }

    @Override
    @Transactional
    public void deleteByDocument(UUID documentId) {
        TenantId tenant = TenantContext.get();
        chunkStore.deleteByDocument(tenant, documentId);
    }
}
