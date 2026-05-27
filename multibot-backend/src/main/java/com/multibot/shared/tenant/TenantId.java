package com.multibot.shared.tenant;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public record TenantId(UUID value) implements Serializable {

    public TenantId {
        Objects.requireNonNull(value, "TenantId value must not be null");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public static TenantId of(String value) {
        return new TenantId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
