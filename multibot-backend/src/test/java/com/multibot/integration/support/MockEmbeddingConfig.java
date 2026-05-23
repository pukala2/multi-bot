package com.multibot.integration.support;

import com.multibot.indexing.domain.port.out.EmbeddingProvider;
import com.multibot.vector.domain.model.Embedding;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Random;

@TestConfiguration
public class MockEmbeddingConfig {

    @Bean(name = "mockEmbeddingProvider")
    @Primary
    public EmbeddingProvider mockEmbeddingProvider() {
        return text -> {
            float[] values = new float[Embedding.DIMENSIONS];
            Random random = new Random(text.hashCode());
            for (int i = 0; i < values.length; i++) {
                values[i] = (random.nextFloat() - 0.5f) * 2;
            }
            float norm = 0f;
            for (float v : values) norm += v * v;
            norm = (float) Math.sqrt(norm);
            if (norm > 0) {
                for (int i = 0; i < values.length; i++) values[i] /= norm;
            }
            return Embedding.of(values);
        };
    }
}
