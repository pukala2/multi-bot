package com.multibot.document.application;

import com.multibot.botversion.domain.port.in.GetActiveVersionUseCase;
import com.multibot.botversion.domain.port.in.VersionDocumentsUseCase;
import com.multibot.document.domain.event.DocumentDeletedEvent;
import com.multibot.document.domain.event.DocumentUploadedEvent;
import com.multibot.document.domain.exception.DocumentNotFoundException;
import com.multibot.document.domain.model.Document;
import com.multibot.document.domain.model.DocumentId;
import com.multibot.document.domain.model.DocumentStatus;
import com.multibot.document.domain.model.UploadDocumentCommand;
import com.multibot.document.domain.port.out.DocumentRepository;
import com.multibot.file.domain.model.StoreFileCommand;
import com.multibot.file.domain.model.StoredFileId;
import com.multibot.file.domain.port.in.DeleteFileUseCase;
import com.multibot.file.domain.port.in.StoreFileUseCase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import com.multibot.vector.domain.port.in.DeleteChunksByDocumentUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private DocumentRepository repository;
    private StoreFileUseCase storeFileUseCase;
    private DeleteFileUseCase deleteFileUseCase;
    private DeleteChunksByDocumentUseCase deleteChunksUseCase;
    private ApplicationEventPublisher events;
    private GetActiveVersionUseCase getActiveVersion;
    private VersionDocumentsUseCase versionDocuments;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        repository = mock(DocumentRepository.class);
        storeFileUseCase = mock(StoreFileUseCase.class);
        deleteFileUseCase = mock(DeleteFileUseCase.class);
        deleteChunksUseCase = mock(DeleteChunksByDocumentUseCase.class);
        events = mock(ApplicationEventPublisher.class);
        getActiveVersion = mock(GetActiveVersionUseCase.class);
        versionDocuments = mock(VersionDocumentsUseCase.class);
        when(getActiveVersion.getActiveVersionId()).thenReturn(Optional.empty());
        service = new DocumentService(repository, storeFileUseCase, deleteFileUseCase,
                deleteChunksUseCase, events, getActiveVersion, versionDocuments);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void upload_storesFileAndPersistsDocument_andPublishesEvent() {
        StoredFileId storedId = StoredFileId.generate();
        when(storeFileUseCase.store(org.mockito.ArgumentMatchers.any(StoreFileCommand.class)))
                .thenReturn(storedId);
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        UploadDocumentCommand cmd = new UploadDocumentCommand("doc.pdf", "application/pdf", "PDF".getBytes());
        Document saved = service.upload(cmd);

        assertThat(saved.tenantId()).isEqualTo(TENANT);
        assertThat(saved.storedFileId()).isEqualTo(storedId);
        assertThat(saved.status()).isEqualTo(DocumentStatus.PENDING);
        assertThat(saved.sizeBytes()).isEqualTo(3);

        ArgumentCaptor<DocumentUploadedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentUploadedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().documentId()).isEqualTo(saved.id());
        assertThat(eventCaptor.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(eventCaptor.getValue().storedFileId()).isEqualTo(storedId);
    }

    @Test
    void getById_returnsDocument() {
        Document document = aDocument();
        when(repository.findById(document.id())).thenReturn(Optional.of(document));

        Document result = service.getById(document.id());

        assertThat(result).isEqualTo(document);
    }

    @Test
    void getById_throwsWhenMissing() {
        DocumentId id = DocumentId.generate();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void delete_cleansChunksDocumentAndFile_thenPublishesEvent() {
        Document document = aDocument();
        when(repository.findById(document.id())).thenReturn(Optional.of(document));

        service.delete(document.id());

        verify(deleteChunksUseCase).deleteByDocument(document.id().value());
        verify(repository).deleteById(document.id());
        verify(deleteFileUseCase).delete(document.storedFileId());

        ArgumentCaptor<DocumentDeletedEvent> eventCaptor = ArgumentCaptor.forClass(DocumentDeletedEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().documentId()).isEqualTo(document.id());
    }

    @Test
    void markAsReady_savesDocumentWithReadyStatus() {
        Document document = aDocument();
        when(repository.findById(document.id())).thenReturn(Optional.of(document));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        Instant indexedAt = Instant.now();
        service.markAsReady(document.id(), indexedAt);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(DocumentStatus.READY);
        assertThat(captor.getValue().indexedAt()).contains(indexedAt);
    }

    @Test
    void markAsFailed_savesDocumentWithError() {
        Document document = aDocument();
        when(repository.findById(document.id())).thenReturn(Optional.of(document));
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        service.markAsFailed(document.id(), "Tika failed");

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(DocumentStatus.FAILED);
        assertThat(captor.getValue().errorMessage()).contains("Tika failed");
    }

    private Document aDocument() {
        Instant now = Instant.now();
        return Document.newlyUploaded(
                DocumentId.generate(),
                TENANT,
                StoredFileId.generate(),
                "report.pdf",
                "application/pdf",
                1024L,
                now);
    }
}
