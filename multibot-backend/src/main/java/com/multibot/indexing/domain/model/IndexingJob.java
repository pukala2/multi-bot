package com.multibot.indexing.domain.model;

import com.multibot.document.domain.model.DocumentId;
import com.multibot.shared.tenant.TenantId;
import com.multibot.shared.tenant.TenantOwned;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record IndexingJob(
        IndexingJobId id,
        TenantId tenantId,
        DocumentId documentId,
        JobStatus status,
        Optional<String> errorMessage,
        Optional<Integer> chunksCreated,
        Instant startedAt,
        Optional<Instant> completedAt,
        Instant createdAt,
        Instant updatedAt
) implements TenantOwned {

    public IndexingJob {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(errorMessage);
        Objects.requireNonNull(chunksCreated);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(completedAt);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static IndexingJob startedNow(IndexingJobId id, TenantId tenantId, DocumentId documentId, Instant now) {
        return new IndexingJob(id, tenantId, documentId, JobStatus.RUNNING,
                Optional.empty(), Optional.empty(), now, Optional.empty(), now, now);
    }

    public IndexingJob completed(int chunksCreated, Instant completedAt) {
        return new IndexingJob(id, tenantId, documentId, JobStatus.COMPLETED,
                Optional.empty(), Optional.of(chunksCreated), startedAt, Optional.of(completedAt),
                createdAt, Instant.now());
    }

    public IndexingJob failed(String errorMessage, Instant completedAt) {
        return new IndexingJob(id, tenantId, documentId, JobStatus.FAILED,
                Optional.of(errorMessage), chunksCreated, startedAt, Optional.of(completedAt),
                createdAt, Instant.now());
    }
}
