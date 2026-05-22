package com.multibot.file;

import com.multibot.file.domain.exception.StoredFileNotFoundException;
import com.multibot.file.domain.model.FileContent;
import com.multibot.file.domain.model.StoreFileCommand;
import com.multibot.file.domain.model.StoredFile;
import com.multibot.file.domain.model.StoredFileId;
import com.multibot.file.domain.port.in.DeleteFileUseCase;
import com.multibot.file.domain.port.in.GetFileMetadataUseCase;
import com.multibot.file.domain.port.in.ReadFileUseCase;
import com.multibot.file.domain.port.in.StoreFileUseCase;
import com.multibot.integration.IntegrationTestBase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilePersistenceIT extends IntegrationTestBase {

    private static final TenantId DEFAULT_TENANT = TenantId.of(
            UUID.fromString("00000000-0000-7000-8000-000000000001"));

    @Autowired
    StoreFileUseCase storeFileUseCase;

    @Autowired
    ReadFileUseCase readFileUseCase;

    @Autowired
    DeleteFileUseCase deleteFileUseCase;

    @Autowired
    GetFileMetadataUseCase getFileMetadataUseCase;

    @BeforeEach
    void setUpTenant() {
        TenantContext.set(DEFAULT_TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("storeFile persists metadata in DB + writes bytes to disk; readFile returns same bytes")
    void shouldStoreAndReadFile() {
        byte[] originalBytes = "Hello multibot file storage".getBytes(StandardCharsets.UTF_8);
        StoreFileCommand cmd = new StoreFileCommand("greeting.txt", "text/plain", originalBytes);

        StoredFileId id = storeFileUseCase.store(cmd);

        FileContent content = readFileUseCase.read(id);
        assertThat(content.bytes()).isEqualTo(originalBytes);
        assertThat(content.mimeType()).isEqualTo("text/plain");

        StoredFile metadata = getFileMetadataUseCase.getMetadata(id);
        assertThat(metadata.originalFilename()).isEqualTo("greeting.txt");
        assertThat(metadata.sizeBytes()).isEqualTo(originalBytes.length);
        assertThat(metadata.tenantId()).isEqualTo(DEFAULT_TENANT);
        assertThat(metadata.storageKey())
                .startsWith("tenants/" + DEFAULT_TENANT.value())
                .endsWith(".txt");
    }

    @Test
    @DisplayName("Stored file is physically present on disk under tenant-scoped path")
    void shouldWriteFileToTenantScopedPath() {
        byte[] bytes = "PDF-CONTENT".getBytes();
        StoreFileCommand cmd = new StoreFileCommand("doc.pdf", "application/pdf", bytes);

        StoredFileId id = storeFileUseCase.store(cmd);
        StoredFile metadata = getFileMetadataUseCase.getMetadata(id);

        Path physicalPath = TEST_STORAGE_DIR.resolve(metadata.storageKey());
        assertThat(Files.exists(physicalPath)).isTrue();
    }

    @Test
    @DisplayName("deleteFile removes both DB record and physical file")
    void shouldDeleteFileAndMetadata() {
        StoredFileId id = storeFileUseCase.store(
                new StoreFileCommand("temp.txt", "text/plain", "data".getBytes()));
        StoredFile metadata = getFileMetadataUseCase.getMetadata(id);
        Path physicalPath = TEST_STORAGE_DIR.resolve(metadata.storageKey());
        assertThat(Files.exists(physicalPath)).isTrue();

        deleteFileUseCase.delete(id);

        assertThatThrownBy(() -> getFileMetadataUseCase.getMetadata(id))
                .isInstanceOf(StoredFileNotFoundException.class);
        assertThat(Files.exists(physicalPath)).isFalse();
    }
}
