package com.multibot.document.infrastructure.persistence;

import com.multibot.document.domain.model.Document;
import com.multibot.document.domain.model.DocumentId;
import com.multibot.document.domain.model.DocumentStatus;
import com.multibot.document.domain.model.DocumentSummary;
import com.multibot.file.domain.model.StoredFileId;
import com.multibot.shared.tenant.TenantId;

import java.util.Optional;

final class DocumentEntityMapper {

    private DocumentEntityMapper() {
    }

    static Document toDomain(DocumentJpaEntity entity) {
        return new Document(
                DocumentId.of(entity.getId()),
                TenantId.of(entity.getTenantId()),
                StoredFileId.of(entity.getStoredFileId()),
                entity.getFilename(),
                entity.getMimeType(),
                entity.getSizeBytes(),
                DocumentStatus.valueOf(entity.getStatus().name()),
                entity.getUploadedAt(),
                Optional.ofNullable(entity.getIndexedAt()),
                Optional.ofNullable(entity.getErrorMessage()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static DocumentSummary toSummary(DocumentJpaEntity entity) {
        return new DocumentSummary(
                DocumentId.of(entity.getId()),
                TenantId.of(entity.getTenantId()),
                entity.getFilename(),
                entity.getMimeType(),
                entity.getSizeBytes(),
                DocumentStatus.valueOf(entity.getStatus().name()),
                entity.getUploadedAt());
    }

    static DocumentJpaEntity toJpaEntity(Document document) {
        return DocumentJpaEntity.builder()
                .id(document.id().value())
                .tenantId(document.tenantId().value())
                .storedFileId(document.storedFileId().value())
                .filename(document.filename())
                .mimeType(document.mimeType())
                .sizeBytes(document.sizeBytes())
                .status(DocumentJpaEntity.DocumentStatusValue.valueOf(document.status().name()))
                .uploadedAt(document.uploadedAt())
                .indexedAt(document.indexedAt().orElse(null))
                .errorMessage(document.errorMessage().orElse(null))
                .createdAt(document.createdAt())
                .updatedAt(document.updatedAt())
                .build();
    }
}
