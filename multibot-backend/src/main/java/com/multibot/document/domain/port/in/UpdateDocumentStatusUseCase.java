package com.multibot.document.domain.port.in;

import com.multibot.document.domain.model.DocumentId;

import java.time.Instant;

public interface UpdateDocumentStatusUseCase {

    void markAsProcessing(DocumentId id);

    void markAsReady(DocumentId id, Instant indexedAt);

    void markAsFailed(DocumentId id, String errorMessage);
}
