package com.multibot.rag.domain.model;

import java.util.List;
import java.util.Objects;

public record RagQuery(String question, List<RagMessage> history, RagOptions options) {

    public RagQuery {
        Objects.requireNonNull(question);
        Objects.requireNonNull(options);
        if (question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        history = history == null ? List.of() : List.copyOf(history);
    }
}
