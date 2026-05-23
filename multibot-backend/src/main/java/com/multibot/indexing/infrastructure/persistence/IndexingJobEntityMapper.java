package com.multibot.indexing.infrastructure.persistence;

import com.multibot.document.domain.model.DocumentId;
import com.multibot.indexing.domain.model.IndexingJob;
import com.multibot.indexing.domain.model.IndexingJobId;
import com.multibot.indexing.domain.model.JobStatus;
import com.multibot.shared.tenant.TenantId;

import java.util.Optional;

final class IndexingJobEntityMapper {

    private IndexingJobEntityMapper() {
    }

    static IndexingJob toDomain(IndexingJobJpaEntity entity) {
        return new IndexingJob(
                IndexingJobId.of(entity.getId()),
                TenantId.of(entity.getTenantId()),
                DocumentId.of(entity.getDocumentId()),
                JobStatus.valueOf(entity.getStatus().name()),
                Optional.ofNullable(entity.getErrorMessage()),
                Optional.ofNullable(entity.getChunksCreated()),
                entity.getStartedAt(),
                Optional.ofNullable(entity.getCompletedAt()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static IndexingJobJpaEntity toJpaEntity(IndexingJob job) {
        return IndexingJobJpaEntity.builder()
                .id(job.id().value())
                .tenantId(job.tenantId().value())
                .documentId(job.documentId().value())
                .status(IndexingJobJpaEntity.JobStatusValue.valueOf(job.status().name()))
                .errorMessage(job.errorMessage().orElse(null))
                .chunksCreated(job.chunksCreated().orElse(null))
                .startedAt(job.startedAt())
                .completedAt(job.completedAt().orElse(null))
                .createdAt(job.createdAt())
                .updatedAt(job.updatedAt())
                .build();
    }
}
