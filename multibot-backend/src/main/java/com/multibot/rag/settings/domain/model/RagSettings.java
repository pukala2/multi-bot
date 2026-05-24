package com.multibot.rag.settings.domain.model;

import java.util.Optional;

public record RagSettings(
        double minScore,
        int topK,
        double temperature,
        Optional<String> systemPrompt) {

    public static RagSettings defaults() {
        return new RagSettings(0.3, 5, 0.2, Optional.empty());
    }
}
