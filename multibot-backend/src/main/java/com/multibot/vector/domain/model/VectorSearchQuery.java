package com.multibot.vector.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * @param versionIdOverride nullable. Jeśli set, search filtruje po tej wersji
 *                          zamiast aktywnej (dla testowego czatu w admin).
 */
public record VectorSearchQuery(
        Embedding queryEmbedding,
        int topK,
        double minScore,
        UUID versionIdOverride) {

    public VectorSearchQuery {
        Objects.requireNonNull(queryEmbedding, "queryEmbedding");
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive, got: " + topK);
        }
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0,1], got: " + minScore);
        }
    }

    public VectorSearchQuery(Embedding queryEmbedding, int topK, double minScore) {
        this(queryEmbedding, topK, minScore, null);
    }
}
