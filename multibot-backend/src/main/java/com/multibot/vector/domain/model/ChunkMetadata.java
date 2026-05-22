package com.multibot.vector.domain.model;

import java.util.Map;
import java.util.Objects;

public record ChunkMetadata(Map<String, Object> values) {

    public static final ChunkMetadata EMPTY = new ChunkMetadata(Map.of());

    public ChunkMetadata {
        Objects.requireNonNull(values, "values");
        values = Map.copyOf(values);
    }

    public static ChunkMetadata of(Map<String, Object> values) {
        return new ChunkMetadata(values);
    }
}
