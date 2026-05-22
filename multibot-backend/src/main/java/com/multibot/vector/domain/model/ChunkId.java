package com.multibot.vector.domain.model;

import com.multibot.shared.util.UuidGenerator;

import java.util.Objects;
import java.util.UUID;

public record ChunkId(UUID value) {

    public ChunkId {
        Objects.requireNonNull(value, "ChunkId value must not be null");
    }

    public static ChunkId of(UUID value) {
        return new ChunkId(value);
    }

    public static ChunkId generate() {
        return new ChunkId(UuidGenerator.newUuidV7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
