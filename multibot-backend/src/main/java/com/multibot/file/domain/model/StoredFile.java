package com.multibot.file.domain.model;

import com.multibot.shared.tenant.TenantId;
import com.multibot.shared.tenant.TenantOwned;

import java.time.Instant;
import java.util.Objects;

public record StoredFile(
        StoredFileId id,
        TenantId tenantId,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String storageKey,
        Instant createdAt
) implements TenantOwned {

    public StoredFile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(originalFilename, "originalFilename");
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(storageKey, "storageKey");
        Objects.requireNonNull(createdAt, "createdAt");
        if (originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename must not be blank");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive, got: " + sizeBytes);
        }
    }
}
