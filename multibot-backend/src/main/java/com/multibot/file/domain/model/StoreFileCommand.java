package com.multibot.file.domain.model;

import java.util.Objects;

public record StoreFileCommand(String originalFilename, String mimeType, byte[] bytes) {

    public StoreFileCommand {
        Objects.requireNonNull(originalFilename, "originalFilename");
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(bytes, "bytes");
        if (originalFilename.isBlank()) {
            throw new IllegalArgumentException("originalFilename must not be blank");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
    }
}
