package com.multibot.vector.domain.model;

import com.multibot.shared.tenant.TenantId;
import com.multibot.shared.tenant.TenantOwned;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Chunk(
        ChunkId id,
        TenantId tenantId,
        UUID versionId,
        UUID documentId,
        int chunkIndex,
        String content,
        Embedding embedding,
        ChunkMetadata metadata,
        Instant createdAt
) implements TenantOwned {

    public Chunk {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(embedding, "embedding");
        Objects.requireNonNull(createdAt, "createdAt");
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex must be non-negative, got: " + chunkIndex);
        }
        if (metadata == null) {
            metadata = ChunkMetadata.EMPTY;
        }
    }
}
