package com.multibot.file.domain.model;

import java.util.Objects;

public record FileContent(byte[] bytes, String mimeType) {

    public FileContent {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(mimeType, "mimeType");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("bytes must not be empty");
        }
    }
}
