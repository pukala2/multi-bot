package com.multibot.rag.domain.port.out;

public interface LlmClient {

    LlmResponse complete(String prompt, double temperature, int maxTokens);

    record LlmResponse(String text, int tokensUsed) {
    }
}
