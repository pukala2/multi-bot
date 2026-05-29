package com.multibot.shared.organization;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record OrganizationId(UUID value) implements Serializable {

    public OrganizationId {
        Objects.requireNonNull(value, "OrganizationId value must not be null");
    }

    public static OrganizationId of(UUID value) {
        return new OrganizationId(value);
    }

    public static OrganizationId of(String value) {
        return new OrganizationId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
