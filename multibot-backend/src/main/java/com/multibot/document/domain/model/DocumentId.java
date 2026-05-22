package com.multibot.document.domain.model;

import com.multibot.shared.util.UuidGenerator;

import java.util.Objects;
import java.util.UUID;

public record DocumentId(UUID value) {

    public DocumentId {
        Objects.requireNonNull(value, "DocumentId value must not be null");
    }

    public static DocumentId of(UUID value) {
        return new DocumentId(value);
    }

    public static DocumentId generate() {
        return new DocumentId(UuidGenerator.newUuidV7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
