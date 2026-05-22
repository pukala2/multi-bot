package com.multibot.file.domain.model;

import com.multibot.shared.util.UuidGenerator;

import java.util.Objects;
import java.util.UUID;

public record StoredFileId(UUID value) {

    public StoredFileId {
        Objects.requireNonNull(value, "StoredFileId value must not be null");
    }

    public static StoredFileId of(UUID value) {
        return new StoredFileId(value);
    }

    public static StoredFileId generate() {
        return new StoredFileId(UuidGenerator.newUuidV7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
