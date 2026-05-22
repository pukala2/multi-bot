package com.multibot.tenant.domain.model;

import com.multibot.shared.tenant.TenantId;
import com.multibot.shared.tenant.TenantOwned;

import java.time.Instant;
import java.util.Objects;

public record Tenant(
        TenantId id,
        String name,
        TenantSlug slug,
        TenantPlan plan,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt
) implements TenantOwned {

    public Tenant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    @Override
    public TenantId tenantId() {
        return id;
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
}
