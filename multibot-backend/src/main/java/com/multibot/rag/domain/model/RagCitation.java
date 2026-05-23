package com.multibot.rag.domain.model;

import java.util.Objects;
import java.util.UUID;

public record RagCitation(UUID chunkId, UUID documentId, String snippet, double score) {

    public RagCitation {
        Objects.requireNonNull(chunkId);
        Objects.requireNonNull(documentId);
        Objects.requireNonNull(snippet);
    }
}
