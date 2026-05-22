package com.multibot.document.application;

import com.multibot.document.domain.event.DocumentDeletedEvent;
import com.multibot.document.domain.event.DocumentUploadedEvent;
import com.multibot.document.domain.exception.DocumentNotFoundException;
import com.multibot.document.domain.model.Document;
import com.multibot.document.domain.model.DocumentId;
import com.multibot.document.domain.model.DocumentSummary;
import com.multibot.document.domain.model.UploadDocumentCommand;
import com.multibot.document.domain.port.in.DeleteDocumentUseCase;
import com.multibot.document.domain.port.in.GetDocumentStatusUseCase;
import com.multibot.document.domain.port.in.GetDocumentUseCase;
import com.multibot.document.domain.port.in.ListDocumentsUseCase;
import com.multibot.document.domain.port.in.UpdateDocumentStatusUseCase;
import com.multibot.document.domain.port.in.UploadDocumentUseCase;
import com.multibot.document.domain.port.out.DocumentRepository;
import com.multibot.file.domain.model.StoreFileCommand;
import com.multibot.file.domain.model.StoredFileId;
import com.multibot.file.domain.port.in.DeleteFileUseCase;
import com.multibot.file.domain.port.in.StoreFileUseCase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import com.multibot.vector.domain.port.in.DeleteChunksByDocumentUseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
class DocumentService implements UploadDocumentUseCase, GetDocumentUseCase, GetDocumentStatusUseCase,
        ListDocumentsUseCase, DeleteDocumentUseCase, UpdateDocumentStatusUseCase {

    private final DocumentRepository repository;
    private final StoreFileUseCase storeFileUseCase;
    private final DeleteFileUseCase deleteFileUseCase;
    private final DeleteChunksByDocumentUseCase deleteChunksUseCase;
    private final ApplicationEventPublisher events;

    DocumentService(DocumentRepository repository,
                    StoreFileUseCase storeFileUseCase,
                    DeleteFileUseCase deleteFileUseCase,
                    DeleteChunksByDocumentUseCase deleteChunksUseCase,
                    ApplicationEventPublisher events) {
        this.repository = repository;
        this.storeFileUseCase = storeFileUseCase;
        this.deleteFileUseCase = deleteFileUseCase;
        this.deleteChunksUseCase = deleteChunksUseCase;
        this.events = events;
    }

    @Override
    @Transactional
    public Document upload(UploadDocumentCommand command) {
        TenantId tenant = TenantContext.get();

        StoredFileId storedFileId = storeFileUseCase.store(
                new StoreFileCommand(command.filename(), command.mimeType(), command.bytes()));

        Document document = Document.newlyUploaded(
                DocumentId.generate(),
                tenant,
                storedFileId,
                command.filename(),
                command.mimeType(),
                command.bytes().length,
                Instant.now());

        Document saved = repository.save(document);

        events.publishEvent(new DocumentUploadedEvent(
                saved.id(),
                saved.tenantId(),
                saved.storedFileId(),
                saved.filename(),
                saved.mimeType(),
                saved.uploadedAt()));

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Document getById(DocumentId id) {
        return repository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Document getStatus(DocumentId id) {
        return getById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentSummary> list(Pageable pageable) {
        return repository.findAllSummaries(pageable);
    }

    @Override
    @Transactional
    public void delete(DocumentId id) {
        Document document = getById(id);

        deleteChunksUseCase.deleteByDocument(document.id().value());
        repository.deleteById(id);
        deleteFileUseCase.delete(document.storedFileId());

        events.publishEvent(new DocumentDeletedEvent(document.id(), document.tenantId()));
    }

    @Override
    @Transactional
    public void markAsProcessing(DocumentId id) {
        Document document = getById(id);
        repository.save(document.withStatus(com.multibot.document.domain.model.DocumentStatus.PROCESSING));
    }

    @Override
    @Transactional
    public void markAsReady(DocumentId id, Instant indexedAt) {
        Document document = getById(id);
        repository.save(document.markAsReady(indexedAt));
    }

    @Override
    @Transactional
    public void markAsFailed(DocumentId id, String errorMessage) {
        Document document = getById(id);
        repository.save(document.markAsFailed(errorMessage));
    }
}
