package com.multibot.rag.domain.model;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;

public record StreamingRagAnswer(Flux<String> tokens, List<RagCitation> citations) {

    public StreamingRagAnswer {
        Objects.requireNonNull(tokens);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
