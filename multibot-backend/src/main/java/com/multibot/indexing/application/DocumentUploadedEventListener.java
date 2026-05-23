package com.multibot.indexing.application;

import com.multibot.document.domain.event.DocumentUploadedEvent;
import com.multibot.indexing.domain.port.in.IndexDocumentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
class DocumentUploadedEventListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadedEventListener.class);

    private final IndexDocumentUseCase indexDocumentUseCase;

    DocumentUploadedEventListener(IndexDocumentUseCase indexDocumentUseCase) {
        this.indexDocumentUseCase = indexDocumentUseCase;
    }

    @ApplicationModuleListener
    void onDocumentUploaded(DocumentUploadedEvent event) {
        log.info("Triggering indexing for document {} (tenant={})", event.documentId(), event.tenantId());
        indexDocumentUseCase.index(event.documentId());
    }
}
