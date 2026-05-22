package com.multibot.file.application;

import com.multibot.file.domain.exception.StoredFileNotFoundException;
import com.multibot.file.domain.model.FileContent;
import com.multibot.file.domain.model.StoreFileCommand;
import com.multibot.file.domain.model.StoredFile;
import com.multibot.file.domain.model.StoredFileId;
import com.multibot.file.domain.port.in.DeleteFileUseCase;
import com.multibot.file.domain.port.in.GetFileMetadataUseCase;
import com.multibot.file.domain.port.in.ReadFileUseCase;
import com.multibot.file.domain.port.in.StoreFileUseCase;
import com.multibot.file.domain.port.out.FileStoragePort;
import com.multibot.file.domain.port.out.StoredFileRepository;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
class FileService implements StoreFileUseCase, ReadFileUseCase, DeleteFileUseCase, GetFileMetadataUseCase {

    private final StoredFileRepository repository;
    private final FileStoragePort storage;

    FileService(StoredFileRepository repository, FileStoragePort storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Override
    @Transactional
    public StoredFileId store(StoreFileCommand command) {
        TenantId tenant = TenantContext.get();
        StoredFileId id = StoredFileId.generate();
        String storageKey = buildStorageKey(tenant, id, command.originalFilename());

        storage.write(storageKey, command.bytes());

        StoredFile metadata = new StoredFile(
                id,
                tenant,
                command.originalFilename(),
                command.mimeType(),
                command.bytes().length,
                storageKey,
                Instant.now());

        return repository.save(metadata).id();
    }

    @Override
    @Transactional(readOnly = true)
    public FileContent read(StoredFileId id) {
        StoredFile metadata = repository.findById(id)
                .orElseThrow(() -> new StoredFileNotFoundException(id));

        byte[] bytes = storage.read(metadata.storageKey());
        return new FileContent(bytes, metadata.mimeType());
    }

    @Override
    @Transactional
    public void delete(StoredFileId id) {
        StoredFile metadata = repository.findById(id)
                .orElseThrow(() -> new StoredFileNotFoundException(id));

        repository.deleteById(id);
        storage.delete(metadata.storageKey());
    }

    @Override
    @Transactional(readOnly = true)
    public StoredFile getMetadata(StoredFileId id) {
        return repository.findById(id)
                .orElseThrow(() -> new StoredFileNotFoundException(id));
    }

    private static String buildStorageKey(TenantId tenant, StoredFileId fileId, String originalFilename) {
        String extension = extractExtension(originalFilename);
        return "tenants/%s/files/%s%s".formatted(tenant.value(), fileId.value(), extension);
    }

    private static String extractExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return (idx > 0 && idx < filename.length() - 1) ? filename.substring(idx) : "";
    }
}
