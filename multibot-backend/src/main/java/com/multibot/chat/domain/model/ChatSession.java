package com.multibot.chat.domain.model;

import com.multibot.shared.tenant.TenantId;
import com.multibot.shared.tenant.TenantOwned;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record ChatSession(
        ChatSessionId id,
        TenantId tenantId,
        EndUserIdentity endUser,
        Instant startedAt,
        Instant lastActivityAt,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) implements TenantOwned {

    public ChatSession {
        Objects.requireNonNull(id);
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(endUser);
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(lastActivityAt);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static ChatSession startedNow(
            ChatSessionId id, TenantId tenantId, EndUserIdentity endUser, Map<String, Object> metadata, Instant now
    ) {
        return new ChatSession(id, tenantId, endUser, now, now, metadata, now, now);
    }

    public ChatSession touchedAt(Instant time) {
        return new ChatSession(id, tenantId, endUser, startedAt, time, metadata, createdAt, Instant.now());
    }
}
