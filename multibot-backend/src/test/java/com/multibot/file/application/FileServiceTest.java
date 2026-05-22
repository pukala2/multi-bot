package com.multibot.file.application;

import com.multibot.file.domain.exception.StoredFileNotFoundException;
import com.multibot.file.domain.model.FileContent;
import com.multibot.file.domain.model.StoreFileCommand;
import com.multibot.file.domain.model.StoredFile;
import com.multibot.file.domain.model.StoredFileId;
import com.multibot.file.domain.port.out.FileStoragePort;
import com.multibot.file.domain.port.out.StoredFileRepository;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.randomUUID());

    private StoredFileRepository repository;
    private FileStoragePort storage;
    private FileService service;

    @BeforeEach
    void setUp() {
        repository = mock(StoredFileRepository.class);
        storage = mock(FileStoragePort.class);
        service = new FileService(repository, storage);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void store_writesBytesAndPersistsMetadata() {
        StoreFileCommand cmd = new StoreFileCommand("doc.pdf", "application/pdf", "PDF-CONTENT".getBytes());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoredFileId id = service.store(cmd);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storage).write(keyCaptor.capture(), eq(cmd.bytes()));
        assertThat(keyCaptor.getValue())
                .startsWith("tenants/" + TENANT.value())
                .endsWith(".pdf")
                .contains("/files/" + id.value());

        ArgumentCaptor<StoredFile> entityCaptor = ArgumentCaptor.forClass(StoredFile.class);
        verify(repository).save(entityCaptor.capture());
        StoredFile saved = entityCaptor.getValue();
        assertThat(saved.tenantId()).isEqualTo(TENANT);
        assertThat(saved.originalFilename()).isEqualTo("doc.pdf");
        assertThat(saved.mimeType()).isEqualTo("application/pdf");
        assertThat(saved.sizeBytes()).isEqualTo(cmd.bytes().length);
    }

    @Test
    void read_returnsBytesAndMimeType() {
        StoredFileId id = StoredFileId.generate();
        StoredFile metadata = aFileMetadata(id, "report.docx", "application/vnd.openxmlformats");
        byte[] bytes = "DOCX".getBytes();
        when(repository.findById(id)).thenReturn(Optional.of(metadata));
        when(storage.read(metadata.storageKey())).thenReturn(bytes);

        FileContent content = service.read(id);

        assertThat(content.bytes()).isEqualTo(bytes);
        assertThat(content.mimeType()).isEqualTo("application/vnd.openxmlformats");
    }

    @Test
    void read_throws_whenMetadataMissing() {
        StoredFileId id = StoredFileId.generate();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read(id))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void delete_removesBothBytesAndMetadata() {
        StoredFileId id = StoredFileId.generate();
        StoredFile metadata = aFileMetadata(id, "old.pdf", "application/pdf");
        when(repository.findById(id)).thenReturn(Optional.of(metadata));

        service.delete(id);

        verify(repository).deleteById(id);
        verify(storage).delete(metadata.storageKey());
    }

    @Test
    void delete_throws_whenMissing() {
        StoredFileId id = StoredFileId.generate();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(StoredFileNotFoundException.class);
    }

    @Test
    void getMetadata_returnsRecord() {
        StoredFileId id = StoredFileId.generate();
        StoredFile metadata = aFileMetadata(id, "x.txt", "text/plain");
        when(repository.findById(id)).thenReturn(Optional.of(metadata));

        StoredFile result = service.getMetadata(id);

        assertThat(result).isEqualTo(metadata);
    }

    private StoredFile aFileMetadata(StoredFileId id, String filename, String mimeType) {
        return new StoredFile(
                id,
                TENANT,
                filename,
                mimeType,
                100L,
                "tenants/" + TENANT.value() + "/files/" + id.value() + ".bin",
                Instant.now());
    }
}
