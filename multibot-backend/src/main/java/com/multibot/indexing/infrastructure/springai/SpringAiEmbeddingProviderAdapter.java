package com.multibot.indexing.infrastructure.springai;

import com.multibot.indexing.domain.port.out.EmbeddingProvider;
import com.multibot.vector.domain.model.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(name = "mockEmbeddingProvider")
class SpringAiEmbeddingProviderAdapter implements EmbeddingProvider {

    private final EmbeddingModel embeddingModel;

    SpringAiEmbeddingProviderAdapter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public Embedding embed(String text) {
        float[] values = embeddingModel.embed(text);
        return Embedding.of(values);
    }
}
