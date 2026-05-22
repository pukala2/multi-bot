package com.multibot.file.infrastructure.persistence;

import com.multibot.file.domain.model.StoredFile;
import com.multibot.file.domain.model.StoredFileId;
import com.multibot.shared.tenant.TenantId;

final class StoredFileEntityMapper {

    private StoredFileEntityMapper() {
    }

    static StoredFile toDomain(StoredFileJpaEntity entity) {
        return new StoredFile(
                StoredFileId.of(entity.getId()),
                TenantId.of(entity.getTenantId()),
                entity.getOriginalFilename(),
                entity.getMimeType(),
                entity.getSizeBytes(),
                entity.getStorageKey(),
                entity.getCreatedAt());
    }

    static StoredFileJpaEntity toJpaEntity(StoredFile file) {
        return StoredFileJpaEntity.builder()
                .id(file.id().value())
                .tenantId(file.tenantId().value())
                .originalFilename(file.originalFilename())
                .mimeType(file.mimeType())
                .sizeBytes(file.sizeBytes())
                .storageKey(file.storageKey())
                .createdAt(file.createdAt())
                .build();
    }
}
