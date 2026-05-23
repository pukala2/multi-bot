package com.multibot.chat.infrastructure.persistence;

import com.multibot.chat.domain.model.ChatSession;
import com.multibot.chat.domain.model.ChatSessionId;
import com.multibot.chat.domain.model.Citation;
import com.multibot.chat.domain.model.EndUserIdentity;
import com.multibot.chat.domain.model.Message;
import com.multibot.chat.domain.model.MessageId;
import com.multibot.chat.domain.model.MessageRole;
import com.multibot.shared.tenant.TenantId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class ChatEntityMapper {

    private ChatEntityMapper() {
    }

    static ChatSession toDomain(ChatSessionJpaEntity entity) {
        EndUserIdentity endUser = entity.getEndUserExternalId() != null
                ? EndUserIdentity.withExternal(entity.getAnonymousId(), entity.getEndUserExternalId())
                : EndUserIdentity.anonymous(entity.getAnonymousId());

        return new ChatSession(
                ChatSessionId.of(entity.getId()),
                TenantId.of(entity.getTenantId()),
                endUser,
                entity.getStartedAt(),
                entity.getLastActivityAt(),
                entity.getMetadata() == null ? Map.of() : entity.getMetadata(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    static ChatSessionJpaEntity toJpaEntity(ChatSession session) {
        return ChatSessionJpaEntity.builder()
                .id(session.id().value())
                .tenantId(session.tenantId().value())
                .endUserExternalId(session.endUser().externalId().orElse(null))
                .anonymousId(session.endUser().anonymousId())
                .startedAt(session.startedAt())
                .lastActivityAt(session.lastActivityAt())
                .metadata(session.metadata().isEmpty() ? null : session.metadata())
                .createdAt(session.createdAt())
                .updatedAt(session.updatedAt())
                .build();
    }

    static Message toDomain(MessageJpaEntity entity) {
        List<Citation> citations = entity.getSources() == null
                ? List.of()
                : entity.getSources().stream().map(ChatEntityMapper::citationFromMap).toList();

        return new Message(
                MessageId.of(entity.getId()),
                TenantId.of(entity.getTenantId()),
                ChatSessionId.of(entity.getSessionId()),
                MessageRole.valueOf(entity.getRole().name()),
                entity.getContent(),
                citations,
                Optional.ofNullable(entity.getTokensUsed()),
                entity.getCreatedAt());
    }

    static MessageJpaEntity toJpaEntity(Message message) {
        List<Map<String, Object>> sourcesJson = message.sources().isEmpty()
                ? null
                : message.sources().stream().map(ChatEntityMapper::citationToMap).toList();

        return MessageJpaEntity.builder()
                .id(message.id().value())
                .tenantId(message.tenantId().value())
                .sessionId(message.sessionId().value())
                .role(MessageJpaEntity.MessageRoleValue.valueOf(message.role().name()))
                .content(message.content())
                .sources(sourcesJson)
                .tokensUsed(message.tokensUsed().orElse(null))
                .createdAt(message.createdAt())
                .build();
    }

    private static Map<String, Object> citationToMap(Citation citation) {
        Map<String, Object> map = new HashMap<>();
        map.put("chunkId", citation.chunkId().toString());
        map.put("documentId", citation.documentId().toString());
        map.put("documentFilename", citation.documentFilename());
        map.put("snippet", citation.snippet());
        map.put("score", citation.score());
        return map;
    }

    private static Citation citationFromMap(Map<String, Object> map) {
        return new Citation(
                UUID.fromString((String) map.get("chunkId")),
                UUID.fromString((String) map.get("documentId")),
                (String) map.get("documentFilename"),
                (String) map.get("snippet"),
                ((Number) map.get("score")).doubleValue());
    }
}
