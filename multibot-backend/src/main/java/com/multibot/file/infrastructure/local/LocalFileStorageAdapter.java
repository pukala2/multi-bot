package com.multibot.file.infrastructure.local;

import com.multibot.file.application.FileStorageProperties;
import com.multibot.file.domain.port.out.FileStoragePort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ConditionalOnProperty(name = "multibot.file.storage.mode", havingValue = "local", matchIfMissing = true)
class LocalFileStorageAdapter implements FileStoragePort {

    private final Path basePath;

    LocalFileStorageAdapter(FileStorageProperties properties) {
        this.basePath = Paths.get(properties.local().basePath()).toAbsolutePath().normalize();
    }

    @Override
    public void write(String storageKey, byte[] bytes) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write file: " + storageKey, e);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        Path source = resolve(storageKey);
        try {
            return Files.readAllBytes(source);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read file: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolve(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete file: " + storageKey, e);
        }
    }

    private Path resolve(String storageKey) {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("Storage key escapes base path: " + storageKey);
        }
        return resolved;
    }
}
