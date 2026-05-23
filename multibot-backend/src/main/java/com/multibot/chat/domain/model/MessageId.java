package com.multibot.chat.domain.model;

import com.multibot.shared.util.UuidGenerator;

import java.util.Objects;
import java.util.UUID;

public record MessageId(UUID value) {

    public MessageId {
        Objects.requireNonNull(value, "MessageId value must not be null");
    }

    public static MessageId of(UUID value) {
        return new MessageId(value);
    }

    public static MessageId generate() {
        return new MessageId(UuidGenerator.newUuidV7());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
