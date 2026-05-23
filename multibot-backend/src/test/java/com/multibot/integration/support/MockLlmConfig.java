package com.multibot.integration.support;

import com.multibot.rag.domain.port.out.LlmClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Flux;

import java.time.Duration;

@TestConfiguration
public class MockLlmConfig {

    @Bean(name = "mockLlmClient")
    @Primary
    public LlmClient mockLlmClient() {
        return new LlmClient() {
            @Override
            public LlmResponse complete(String prompt, double temperature, int maxTokens) {
                String answer = "Mock answer based on " + countContextChunks(prompt) + " context chunks.";
                return new LlmResponse(answer, answer.length() / 4);
            }

            @Override
            public Flux<String> stream(String prompt, double temperature, int maxTokens) {
                String answer = "Mock answer based on " + countContextChunks(prompt) + " context chunks.";
                String[] words = answer.split(" ");
                return Flux.fromArray(words)
                        .delayElements(Duration.ofMillis(5))
                        .map(word -> word + " ");
            }
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
