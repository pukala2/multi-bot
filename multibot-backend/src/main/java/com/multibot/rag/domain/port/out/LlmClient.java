package com.multibot.rag.domain.port.out;

import reactor.core.publisher.Flux;

public interface LlmClient {

    LlmResponse complete(String prompt, double temperature, int maxTokens);

    Flux<String> stream(String prompt, double temperature, int maxTokens);

    record LlmResponse(String text, int tokensUsed) {
    }
}
