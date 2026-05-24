package com.multibot.chat.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * @param versionIdOverride nullable. Override aktywnej wersji bota (do
 *                          testowego czatu w admin panel, gdzie operator chce
 *                          zapytać DRAFT v2 zanim ją opublikuje).
 */
public record SendMessageCommand(ChatSessionId sessionId, String content, UUID versionIdOverride) {

    public SendMessageCommand {
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(content);
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        if (content.length() > 4000) {
            throw new IllegalArgumentException("content too long (max 4000 chars)");
        }
    }

    public SendMessageCommand(ChatSessionId sessionId, String content) {
        this(sessionId, content, null);
    }
}
