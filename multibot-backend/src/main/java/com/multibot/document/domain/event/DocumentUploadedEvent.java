package com.multibot.document.domain.event;

import com.multibot.document.domain.model.DocumentId;
import com.multibot.file.domain.model.StoredFileId;
import com.multibot.shared.tenant.TenantId;

import java.time.Instant;

public record DocumentUploadedEvent(
        DocumentId documentId,
        TenantId tenantId,
        StoredFileId storedFileId,
        String filename,
        String mimeType,
        Instant uploadedAt
) {
}
