package com.multibot.vector.domain.model;

import java.util.Objects;

public record Embedding(float[] values) {

    public static final int DIMENSIONS = 1536;

    public Embedding {
        Objects.requireNonNull(values, "values must not be null");
        if (values.length != DIMENSIONS) {
            throw new IllegalArgumentException(
                    "Embedding must have exactly %d dimensions, got %d".formatted(DIMENSIONS, values.length));
        }
    }

    public static Embedding of(float[] values) {
        return new Embedding(values);
    }
}
