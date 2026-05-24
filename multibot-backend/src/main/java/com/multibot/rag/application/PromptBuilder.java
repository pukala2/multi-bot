package com.multibot.rag.application;

import com.multibot.rag.domain.model.RagMessage;
import com.multibot.vector.domain.model.VectorSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class PromptBuilder {

    private static final String DEFAULT_SYSTEM_PROMPT = """
            Jesteś pomocnym asystentem firmy. Odpowiadasz na pytania użytkowników WYŁĄCZNIE
            na podstawie poniższych fragmentów dokumentów firmowych (KONTEKST).

            Zasady:
            - Jeśli odpowiedź NIE znajduje się w kontekście, odpowiedz: "Nie znam odpowiedzi na to pytanie."
            - Nie wymyślaj informacji. Nie zgaduj.
            - Cytuj numery źródeł w nawiasach kwadratowych [1], [2] gdzie to istotne.
            - Odpowiadaj zwięźle i konkretnie.
            - Używaj języka polskiego.
            """;

    private final String globalSystemPrompt;

    PromptBuilder(@Value("${multibot.rag.system-prompt:}") String systemPromptOverride) {
        this.globalSystemPrompt = (systemPromptOverride == null || systemPromptOverride.isBlank())
                ? DEFAULT_SYSTEM_PROMPT
                : systemPromptOverride;
    }

    String build(List<VectorSearchResult> chunks, List<RagMessage> history, String question) {
        return build(chunks, history, question, Optional.empty());
    }

    String build(List<VectorSearchResult> chunks, List<RagMessage> history, String question,
                 Optional<String> tenantSystemPrompt) {
        String systemPrompt = tenantSystemPrompt
                .filter(p -> !p.isBlank())
                .orElse(globalSystemPrompt);

        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt).append("\n\n");

        if (chunks.isEmpty()) {
            sb.append("[KONTEKST]\n(brak relevantnych fragmentów dokumentów)\n\n");
        } else {
            sb.append("[KONTEKST]\n");
            for (int i = 0; i < chunks.size(); i++) {
                VectorSearchResult result = chunks.get(i);
                sb.append("[").append(i + 1).append("] ")
                        .append(result.chunk().content())
                        .append("\n\n");
            }
        }

        if (!history.isEmpty()) {
            sb.append("[HISTORIA ROZMOWY]\n");
            for (RagMessage msg : history) {
                sb.append(msg.role() == RagMessage.Role.USER ? "User: " : "Assistant: ")
                        .append(msg.content()).append("\n");
            }
            sb.append("\n");
        }

        sb.append("[AKTUALNE PYTANIE]\n").append(question);
        return sb.toString();
    }
}
