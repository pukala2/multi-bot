package com.multibot.indexing.application;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
class ChunkingPolicy {

    private final TokenTextSplitter splitter;

    ChunkingPolicy(TokenTextSplitter splitter) {
        this.splitter = splitter;
    }

    List<String> split(String text) {
        List<Document> documents = splitter.apply(List.of(new Document(text, Map.of())));
        return documents.stream()
                .map(Document::getText)
                .filter(content -> content != null && !content.isBlank())
                .toList();
    }
}
