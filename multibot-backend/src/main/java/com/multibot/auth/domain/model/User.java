package com.multibot.auth.domain.model;

import com.multibot.shared.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Konto uzytkownika panelu admin lub super-admina platformy.
 *
 * Pola nullable (tenantId, createdBy, lastLoginAt) zamiast Optional — Optional w polach to
 * anti-pattern (nie Serializable, marnuje pamiec, trzy stany zamiast dwoch).
 * Optional uzywaj tylko jako TYP ZWRACANY z metody — tu mamy np. {@link #tenantIdOpt()}.
 */
public record User(
        UserId id,
        String email,
        String passwordHash,
        Role role,
        UserStatus status,
        TenantId tenantId,        // nullable: null gdy SUPER_ADMIN
        UserId createdBy,         // nullable: null dla bootstrap'owanych userow
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt       // nullable: null gdy nigdy sie nie logowal
) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        if (email.isBlank()) throw new IllegalArgumentException("email is blank");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");

        if (role == Role.SUPER_ADMIN && tenantId != null) {
            throw new IllegalArgumentException("SUPER_ADMIN must not have tenantId");
        }
        if (role.isTenantRole() && tenantId == null) {
            throw new IllegalArgumentException("Role " + role + " requires tenantId");
        }
    }

    public Optional<TenantId> tenantIdOpt() {
        return Optional.ofNullable(tenantId);
    }

    public Optional<UserId> createdByOpt() {
        return Optional.ofNullable(createdBy);
    }

    public Optional<Instant> lastLoginAtOpt() {
        return Optional.ofNullable(lastLoginAt);
    }

    public static User newSuperAdmin(UserId id, String email, String passwordHash, Instant now) {
        return new User(id, email, passwordHash, Role.SUPER_ADMIN, UserStatus.ACTIVE,
                null, null, now, now, null);
    }

    public static User newTenantUser(UserId id, String email, String passwordHash,
                                     Role role, TenantId tenant, UserId createdBy,
                                     UserStatus initialStatus, Instant now) {
        return new User(id, email, passwordHash, role, initialStatus,
                tenant, createdBy, now, now, null);
    }

    public User withLastLoginAt(Instant when) {
        return new User(id, email, passwordHash, role, status, tenantId, createdBy,
                createdAt, when, when);
    }

    public User withStatus(UserStatus newStatus, Instant now) {
        return new User(id, email, passwordHash, role, newStatus, tenantId, createdBy,
                createdAt, now, lastLoginAt);
    }

    public User withPasswordHash(String newHash, Instant now) {
        return new User(id, email, newHash, role, status, tenantId, createdBy,
                createdAt, now, lastLoginAt);
    }
}
