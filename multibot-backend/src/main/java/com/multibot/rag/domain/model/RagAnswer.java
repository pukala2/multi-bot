package com.multibot.rag.domain.model;

import java.util.List;
import java.util.Objects;

public record RagAnswer(String text, List<RagCitation> citations, int tokensUsed) {

    public RagAnswer {
        Objects.requireNonNull(text);
        citations = citations == null ? List.of() : List.copyOf(citations);
        if (tokensUsed < 0) {
            throw new IllegalArgumentException("tokensUsed must be non-negative");
        }
    }
}
