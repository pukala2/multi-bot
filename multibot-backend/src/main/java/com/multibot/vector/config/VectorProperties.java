package com.multibot.vector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "multibot.rag")
public record VectorProperties(int candidateMultiplier) {

    public VectorProperties {
        if (candidateMultiplier <= 0) {
            throw new IllegalArgumentException("candidateMultiplier must be positive, got: " + candidateMultiplier);
        }
    }
}
