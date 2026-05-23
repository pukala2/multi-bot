package com.multibot.chat.domain.model;

import java.util.Objects;
import java.util.UUID;

public record Citation(
        UUID chunkId,
        UUID documentId,
        String documentFilename,
        String snippet,
        double score
) {

    public Citation {
        Objects.requireNonNull(chunkId);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(documentFilename);
        Objects.requireNonNull(snippet);
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("score must be in [0,1], got: " + score);
        }
    }
}
