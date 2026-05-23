package com.multibot.rag.domain.model;

public record RagOptions(int topK, double minScore, double temperature, int maxTokens) {

    public RagOptions {
        if (topK <= 0) throw new IllegalArgumentException("topK must be positive");
        if (minScore < 0.0 || minScore > 1.0) throw new IllegalArgumentException("minScore must be in [0,1]");
        if (temperature < 0.0 || temperature > 2.0) throw new IllegalArgumentException("temperature must be in [0,2]");
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive");
    }

    public static RagOptions defaults() {
        return new RagOptions(5, 0.7, 0.2, 1024);
    }
}
