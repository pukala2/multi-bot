package com.multibot.vector.domain.port.out;

import com.multibot.shared.tenant.TenantId;
import com.multibot.vector.domain.model.Chunk;
import com.multibot.vector.domain.model.Embedding;
import com.multibot.vector.domain.model.VectorSearchResult;

import java.util.List;
import java.util.UUID;

public interface ChunkStore {

    void storeAll(List<Chunk> chunks);

    List<VectorSearchResult> search(TenantId tenantId, UUID versionId, Embedding queryEmbedding, int candidateLimit);

    void deleteByDocument(TenantId tenantId, UUID documentId);

    void copyChunks(UUID fromVersionId, UUID toVersionId, java.util.Map<UUID, UUID> documentIdMapping);
}
