package com.multibot.vector.domain.model;

import java.util.Objects;

public record VectorSearchQuery(Embedding queryEmbedding, int topK, double minScore) {

    public VectorSearchQuery {
        Objects.requireNonNull(queryEmbedding, "queryEmbedding");
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive, got: " + topK);
        }
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0,1], got: " + minScore);
        }
    }
}
