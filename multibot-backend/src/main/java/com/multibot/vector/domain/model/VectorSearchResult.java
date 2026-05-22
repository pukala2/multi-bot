package com.multibot.vector.domain.model;

import java.util.Objects;

public record VectorSearchResult(Chunk chunk, double similarity) {

    public VectorSearchResult {
        Objects.requireNonNull(chunk, "chunk");
        if (similarity < 0.0 || similarity > 1.0) {
            throw new IllegalArgumentException("similarity must be in [0,1], got: " + similarity);
        }
    }
}
