package com.multibot.chat.domain.model;

import com.multibot.shared.util.UuidGenerator;

import java.util.Objects;
import java.util.Optional;

public record EndUserIdentity(String anonymousId, Optional<String> externalId) {

    public EndUserIdentity {
        Objects.requireNonNull(anonymousId, "anonymousId");
        Objects.requireNonNull(externalId, "externalId");
        if (anonymousId.isBlank()) {
            throw new IllegalArgumentException("anonymousId must not be blank");
        }
    }

    public static EndUserIdentity anonymous() {
        return new EndUserIdentity("anon-" + UuidGenerator.newUuidV7(), Optional.empty());
    }

    public static EndUserIdentity anonymous(String anonymousId) {
        return new EndUserIdentity(anonymousId, Optional.empty());
    }

    public static EndUserIdentity withExternal(String anonymousId, String externalId) {
        return new EndUserIdentity(anonymousId, Optional.of(externalId));
    }
}
