package com.multibot.document.domain.model;

import java.util.Objects;

public record UploadDocumentCommand(String filename, String mimeType, byte[] bytes) {

    public UploadDocumentCommand {
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(mimeType, "mimeType");
        Objects.requireNonNull(bytes, "bytes");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
    }
}
