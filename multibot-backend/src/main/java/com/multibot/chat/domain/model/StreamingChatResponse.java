package com.multibot.chat.domain.model;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public record StreamingChatResponse(
        MessageId assistantMessageId,
        List<Citation> citations,
        Flux<String> tokens,
        BiConsumer<String, Integer> onComplete,
        Consumer<Throwable> onError
) {

    public StreamingChatResponse {
        Objects.requireNonNull(assistantMessageId);
        Objects.requireNonNull(tokens);
        Objects.requireNonNull(onComplete);
        Objects.requireNonNull(onError);
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
