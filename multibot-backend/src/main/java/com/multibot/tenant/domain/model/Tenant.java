package com.multibot.tenant.domain.model;

import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.tenant.TenantId;
import com.multibot.shared.tenant.TenantOwned;

import java.time.Instant;
import java.util.Objects;

/**
 * Tenant = BOT klienta. Granica izolacji danych (dokumenty, wektory, wersje, widget, czat, usage).
 * Plan, limity i dane firmowe sa na poziomie organizacji ({@link OrganizationId}), nie tutaj —
 * wszystkie boty jednej organizacji wspoldziela limit wiadomosci.
 */
public record Tenant(
        TenantId id,
        String name,
        TenantSlug slug,
        OrganizationId organizationId,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt
) implements TenantOwned {

    public Tenant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(slug, "slug");
        Objects.requireNonNull(organizationId, "organizationId");
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
