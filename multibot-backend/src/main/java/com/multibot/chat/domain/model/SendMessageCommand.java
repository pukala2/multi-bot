package com.multibot.chat.domain.model;

import java.util.Objects;

public record SendMessageCommand(ChatSessionId sessionId, String content) {

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
}
