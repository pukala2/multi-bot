package com.multibot.rag.infrastructure.springai;

import com.multibot.rag.domain.model.LlmPrompt;
import com.multibot.rag.domain.port.out.LlmClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@ConditionalOnMissingBean(name = "mockLlmClient")
class SpringAiLlmClientAdapter implements LlmClient {

    private final ChatClient chatClient;

    SpringAiLlmClientAdapter(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public LlmResponse complete(LlmPrompt prompt, double temperature, int maxTokens) {
        // System prompt jako rola systemowa, niezaufane dane jako user — rozdział ról (anti-injection).
        String text = chatClient.prompt()
                .system(prompt.system())
                .user(prompt.user())
                .call().content();
        if (text == null) {
            text = "";
        }
        int approxTokens = estimateTokens(text);
        return new LlmResponse(text, approxTokens);
    }

    @Override
    public Flux<String> stream(LlmPrompt prompt, double temperature, int maxTokens) {
        return chatClient.prompt()
                .system(prompt.system())
                .user(prompt.user())
                .stream().content();
    }

    private static int estimateTokens(String text) {
        return text.length() / 4;
    }
}
