package com.multibot.chat.domain.model;

import com.multibot.shared.tenant.TenantId;
import com.multibot.shared.tenant.TenantOwned;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record Message(
        MessageId id,
        TenantId tenantId,
        ChatSessionId sessionId,
        MessageRole role,
        String content,
        List<Citation> sources,
        Optional<Integer> tokensUsed,
        Instant createdAt
) implements TenantOwned {

    public Message {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(sessionId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(content);
        Objects.requireNonNull(tokensUsed);
        Objects.requireNonNull(createdAt);
        if (content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
        sources = sources == null ? List.of() : List.copyOf(sources);
    }

    public static Message userMessage(TenantId tenantId, ChatSessionId sessionId, String content, Instant now) {
        return new Message(MessageId.generate(), tenantId, sessionId, MessageRole.USER, content,
                List.of(), Optional.empty(), now);
    }

    public static Message assistantMessage(
            TenantId tenantId, ChatSessionId sessionId, String content,
            List<Citation> sources, int tokensUsed, Instant now
    ) {
        return new Message(MessageId.generate(), tenantId, sessionId, MessageRole.ASSISTANT, content,
                sources, Optional.of(tokensUsed), now);
    }
}
