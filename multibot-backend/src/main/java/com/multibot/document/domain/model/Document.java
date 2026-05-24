package com.multibot.document.domain.model;

import com.multibot.file.domain.model.StoredFileId;
import com.multibot.shared.tenant.TenantId;
import com.multibot.shared.tenant.TenantOwned;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Document(
        DocumentId id,
        TenantId tenantId,
        UUID versionId,
        StoredFileId storedFileId,
        String filename,
        String mimeType,
        long sizeBytes,
        DocumentStatus status,
        Instant uploadedAt,
        Optional<Instant> indexedAt,
        Optional<String> errorMessage,
        Instant createdAt,
        Instant updatedAt
) implements TenantOwned {

    public Document {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(storedFileId, "storedFileId");
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(uploadedAt, "uploadedAt");
        Objects.requireNonNull(indexedAt, "indexedAt");
        Objects.requireNonNull(errorMessage, "errorMessage");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive, got: " + sizeBytes);
        }
    }

    public static Document newlyUploaded(
            DocumentId id,
            TenantId tenantId,
            UUID versionId,
            StoredFileId storedFileId,
            String filename,
            String mimeType,
            long sizeBytes,
            Instant now
    ) {
        return new Document(
                id,
                tenantId,
                versionId,
                storedFileId,
                filename,
                mimeType,
                sizeBytes,
                DocumentStatus.PENDING,
                now,
                Optional.empty(),
                Optional.empty(),
                now,
                now);
    }

    public Document withStatus(DocumentStatus newStatus) {
        return new Document(
                id, tenantId, versionId, storedFileId, filename, mimeType, sizeBytes,
                newStatus, uploadedAt, indexedAt, errorMessage, createdAt, Instant.now());
    }

    public Document markAsReady(Instant indexedAt) {
        return new Document(
                id, tenantId, versionId, storedFileId, filename, mimeType, sizeBytes,
                DocumentStatus.READY, uploadedAt, Optional.of(indexedAt), Optional.empty(),
                createdAt, Instant.now());
    }

    public Document markAsFailed(String error) {
        return new Document(
                id, tenantId, versionId, storedFileId, filename, mimeType, sizeBytes,
                DocumentStatus.FAILED, uploadedAt, indexedAt, Optional.of(error),
                createdAt, Instant.now());
    }
}
