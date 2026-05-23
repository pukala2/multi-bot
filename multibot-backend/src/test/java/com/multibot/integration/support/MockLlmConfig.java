package com.multibot.integration.support;

import com.multibot.rag.domain.port.out.LlmClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class MockLlmConfig {

    @Bean(name = "mockLlmClient")
    @Primary
    public LlmClient mockLlmClient() {
        return (prompt, temperature, maxTokens) -> {
            String answer = "Mock answer based on " + countContextChunks(prompt) + " context chunks.";
            return new LlmClient.LlmResponse(answer, answer.length() / 4);
        };
    }

    private static int countContextChunks(String prompt) {
        int count = 0;
        int index = 0;
        while ((index = prompt.indexOf("[" + (count + 1) + "]", index)) != -1) {
            count++;
            index++;
        }
        return count;
    }
}
