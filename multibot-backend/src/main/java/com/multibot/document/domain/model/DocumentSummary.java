package com.multibot.document.domain.model;

import com.multibot.shared.tenant.TenantId;
import com.multibot.shared.tenant.TenantOwned;

import java.time.Instant;
import java.util.Objects;

public record DocumentSummary(
        DocumentId id,
        TenantId tenantId,
        String filename,
        String mimeType,
        long sizeBytes,
        DocumentStatus status,
        Instant uploadedAt
) implements TenantOwned {

    public DocumentSummary {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(filename);
        Objects.requireNonNull(mimeType);
        Objects.requireNonNull(status);
        Objects.requireNonNull(uploadedAt);
    }
}
