package com.multibot.indexing.domain.event;

import com.multibot.document.domain.model.DocumentId;
import com.multibot.shared.tenant.TenantId;

import java.time.Instant;

public record IndexingCompletedEvent(
        DocumentId documentId,
        TenantId tenantId,
        int chunksCreated,
        Instant completedAt
) {
}
