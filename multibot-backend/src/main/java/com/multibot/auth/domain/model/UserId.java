package com.multibot.auth.domain.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) implements Serializable {

    public UserId {
        Objects.requireNonNull(value, "UserId value must not be null");
    }

    public static UserId of(UUID value) {
        return new UserId(value);
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }
}
