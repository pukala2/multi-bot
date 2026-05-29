package com.multibot.organization.domain.model;

import com.multibot.shared.organization.OrganizationId;

import java.time.Instant;
import java.util.Objects;

/**
 * Organizacja = konto klienta platformy. Stoi ponad botami (tenantami).
 * Plan, limit wiadomosci (wspolny dla wszystkich botow) i dane firmowe sa tutaj.
 *
 * plan to KOD planu z tabeli billing_plans (FREE/BASIC/PRO/ENTERPRISE/...), nie enum —
 * dzieki temu super admin moze dodawac/edytowac plany w bazie bez zmiany kodu.
 */
public record Organization(
        OrganizationId id,
        String name,
        OrganizationSlug slug,
        String plan,
        OrganizationStatus status,
        String companyName,                    // nullable
        String nip,                            // nullable
        Integer monthlyMessagesLimitOverride,  // nullable; -1 = unlimited
        Instant createdAt,
        Instant updatedAt
) {

    public Organization {
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

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }
}
