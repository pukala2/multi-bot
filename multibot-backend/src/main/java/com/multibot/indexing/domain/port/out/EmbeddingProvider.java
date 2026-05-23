package com.multibot.indexing.domain.port.out;

import com.multibot.vector.domain.model.Embedding;

public interface EmbeddingProvider {

    Embedding embed(String text);
}
