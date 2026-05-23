package com.multibot.indexing.domain.model;

import java.util.Objects;
import java.util.Optional;

public record ExtractedText(String content, Optional<String> language, String detectedMimeType) {

    public ExtractedText {
        Objects.requireNonNull(content);
        Objects.requireNonNull(language);
        Objects.requireNonNull(detectedMimeType);
    }

    public boolean isEmpty() {
        return content.isBlank();
    }
}
