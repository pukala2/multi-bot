package com.multibot.rag.domain.port.out;

import com.multibot.rag.domain.model.LlmPrompt;
import reactor.core.publisher.Flux;

public interface LlmClient {

    LlmResponse complete(LlmPrompt prompt, double temperature, int maxTokens);

    Flux<String> stream(LlmPrompt prompt, double temperature, int maxTokens);

    record LlmResponse(String text, int tokensUsed) {
    }
}
