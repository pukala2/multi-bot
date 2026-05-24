package com.multibot.rag.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * @param versionIdOverride nullable. Jeśli set, RAG odpyta dokumenty tej wersji
 *                          zamiast aktywnej (do testowego czatu w admin panel).
 */
public record RagQuery(
        String question,
        List<RagMessage> history,
        RagOptions options,
        UUID versionIdOverride) {

    public RagQuery {
        Objects.requireNonNull(question);
        Objects.requireNonNull(options);
        if (question.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        history = history == null ? List.of() : List.copyOf(history);
    }

    public RagQuery(String question, List<RagMessage> history, RagOptions options) {
        this(question, history, options, null);
    }
}
