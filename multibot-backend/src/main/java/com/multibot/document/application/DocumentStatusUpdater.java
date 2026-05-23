package com.multibot.document.application;

import com.multibot.document.domain.port.in.UpdateDocumentStatusUseCase;
import com.multibot.indexing.domain.event.IndexingCompletedEvent;
import com.multibot.indexing.domain.event.IndexingFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class DocumentStatusUpdater {

    private static final Logger log = LoggerFactory.getLogger(DocumentStatusUpdater.class);

    private final UpdateDocumentStatusUseCase updateDocumentStatusUseCase;

    DocumentStatusUpdater(UpdateDocumentStatusUseCase updateDocumentStatusUseCase) {
        this.updateDocumentStatusUseCase = updateDocumentStatusUseCase;
    }

    @ApplicationModuleListener
    void onIndexingCompleted(IndexingCompletedEvent event) {
        log.info("Marking document {} as READY (chunks={})", event.documentId(), event.chunksCreated());
        updateDocumentStatusUseCase.markAsReady(event.documentId(), event.completedAt());
    }

    @ApplicationModuleListener
    void onIndexingFailed(IndexingFailedEvent event) {
        log.warn("Marking document {} as FAILED: {}", event.documentId(), event.errorMessage());
        updateDocumentStatusUseCase.markAsFailed(event.documentId(), event.errorMessage());
    }
}
