package com.multibot.rag.application;

import com.multibot.indexing.domain.port.out.EmbeddingProvider;
import com.multibot.rag.domain.model.RagAnswer;
import com.multibot.rag.domain.model.RagCitation;
import com.multibot.rag.domain.model.RagQuery;
import com.multibot.rag.domain.model.StreamingRagAnswer;
import com.multibot.rag.domain.port.in.AnswerQuestionUseCase;
import com.multibot.rag.domain.port.out.LlmClient;
import com.multibot.rag.settings.domain.model.RagSettings;
import com.multibot.rag.settings.domain.port.in.GetRagSettingsUseCase;
import com.multibot.vector.domain.model.Embedding;
import com.multibot.vector.domain.model.VectorSearchQuery;
import com.multibot.vector.domain.model.VectorSearchResult;
import com.multibot.vector.domain.port.in.SearchChunksUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
class RagService implements AnswerQuestionUseCase {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int SNIPPET_LENGTH = 200;

    private final EmbeddingProvider embeddingProvider;
    private final SearchChunksUseCase searchChunksUseCase;
    private final PromptBuilder promptBuilder;
    private final LlmClient llmClient;
    private final GetRagSettingsUseCase getRagSettings;

    RagService(EmbeddingProvider embeddingProvider,
               SearchChunksUseCase searchChunksUseCase,
               PromptBuilder promptBuilder,
               LlmClient llmClient,
               GetRagSettingsUseCase getRagSettings) {
        this.embeddingProvider = embeddingProvider;
        this.searchChunksUseCase = searchChunksUseCase;
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.getRagSettings = getRagSettings;
    }

    @Override
    public RagAnswer answer(RagQuery query) {
        RagSettings settings = getRagSettings.getForCurrentTenant();
        int topK = settings.topK();
        double minScore = settings.minScore();
        double temperature = settings.temperature();
        int maxTokens = query.options().maxTokens();

        Embedding queryEmbedding = embeddingProvider.embed(query.question());
        List<VectorSearchResult> chunks = searchChunksUseCase.search(
                new VectorSearchQuery(queryEmbedding, topK, minScore, query.versionIdOverride()));

        logRetrieval(query.question(), topK, minScore, chunks);

        String prompt = promptBuilder.build(chunks, query.history(), query.question(),
                settings.systemPrompt());

        LlmClient.LlmResponse response = llmClient.complete(prompt, temperature, maxTokens);

        List<RagCitation> citations = chunks.stream().map(this::toCitation).toList();

        return new RagAnswer(response.text(), citations, response.tokensUsed());
    }

    @Override
    public StreamingRagAnswer streamAnswer(RagQuery query) {
        RagSettings settings = getRagSettings.getForCurrentTenant();
        int topK = settings.topK();
        double minScore = settings.minScore();
        double temperature = settings.temperature();
        int maxTokens = query.options().maxTokens();

        Embedding queryEmbedding = embeddingProvider.embed(query.question());
        List<VectorSearchResult> chunks = searchChunksUseCase.search(
                new VectorSearchQuery(queryEmbedding, topK, minScore, query.versionIdOverride()));

        logRetrieval(query.question(), topK, minScore, chunks);

        String prompt = promptBuilder.build(chunks, query.history(), query.question(),
                settings.systemPrompt());

        List<RagCitation> citations = chunks.stream().map(this::toCitation).toList();

        return new StreamingRagAnswer(
                llmClient.stream(prompt, temperature, maxTokens),
                citations);
    }

    private void logRetrieval(String question, int topK, double minScore, List<VectorSearchResult> chunks) {
        if (chunks.isEmpty()) {
            log.warn("RAG retrieval: 0 chunks above threshold {} for question='{}' (topK={})",
                    minScore, question, topK);
            return;
        }
        String scores = chunks.stream()
                .map(r -> "%.3f".formatted(r.similarity()))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        log.info("RAG retrieval: {} chunks for question='{}' scores=[{}]",
                chunks.size(), question, scores);
    }

    private RagCitation toCitation(VectorSearchResult result) {
        String content = result.chunk().content();
        String snippet = content.length() <= SNIPPET_LENGTH
                ? content
                : content.substring(0, SNIPPET_LENGTH).trim() + "…";
        return new RagCitation(
                result.chunk().id().value(),
                result.chunk().documentId(),
                snippet,
                result.similarity());
    }
}
