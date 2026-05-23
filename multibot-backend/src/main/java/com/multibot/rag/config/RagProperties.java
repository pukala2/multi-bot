package com.multibot.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "multibot.rag")
public record RagProperties(
        int topK,
        double minScore,
        double temperature,
        int maxTokens) {

    public RagProperties {
        if (topK <= 0) throw new IllegalArgumentException("topK must be positive, got: " + topK);
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0,1], got: " + minScore);
        }
        if (temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be in [0,2], got: " + temperature);
        }
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be positive, got: " + maxTokens);
    }
}
