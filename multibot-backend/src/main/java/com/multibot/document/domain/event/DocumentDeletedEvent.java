package com.multibot.document.domain.event;

import com.multibot.document.domain.model.DocumentId;
import com.multibot.shared.tenant.TenantId;

public record DocumentDeletedEvent(DocumentId documentId, TenantId tenantId) {
}
