package com.multibot.chat.domain.model;

import com.multibot.shared.util.UuidGenerator;

import java.util.Objects;
import java.util.UUID;

public record ChatSessionId(UUID value) {

    public ChatSessionId {
        Objects.requireNonNull(value, "ChatSessionId value must not be null");
    }

    public static ChatSessionId of(UUID value) {
        return new ChatSessionId(value);
    }

    public static ChatSessionId generate() {
        return new ChatSessionId(UuidGenerator.newUuidV7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
