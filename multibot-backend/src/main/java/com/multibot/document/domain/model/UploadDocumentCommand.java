package com.multibot.document.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record UploadDocumentCommand(
        String filename, String mimeType, byte[] bytes, Optional<UUID> versionId) {

    public UploadDocumentCommand {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(versionId, "versionId");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
    }

    public UploadDocumentCommand(String filename, String mimeType, byte[] bytes) {
        this(filename, mimeType, bytes, Optional.empty());
    }
}
