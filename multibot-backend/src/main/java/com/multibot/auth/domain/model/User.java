package com.multibot.auth.domain.model;

import com.multibot.shared.organization.OrganizationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Konto uzytkownika panelu admin lub super-admina platformy.
 *
 * User nalezy do ORGANIZACJI (klienta), nie do pojedynczego bota — zarzadza wszystkimi botami swojej org.
 *
 * Pola nullable (organizationId, createdBy, lastLoginAt) zamiast Optional — Optional w polach to
 * anti-pattern (nie Serializable, marnuje pamiec, trzy stany zamiast dwoch).
 * Optional uzywaj tylko jako TYP ZWRACANY z metody — tu mamy np. {@link #organizationIdOpt()}.
 */
public record User(
        UserId id,
        String email,
        String passwordHash,
        Role role,
        UserStatus status,
        OrganizationId organizationId,   // nullable: null gdy SUPER_ADMIN
        UserId createdBy,                // nullable: null dla bootstrap'owanych userow
        Instant createdAt,
        Instant updatedAt,
        Instant lastLoginAt              // nullable: null gdy nigdy sie nie logowal
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

        if (role == Role.SUPER_ADMIN && organizationId != null) {
            throw new IllegalArgumentException("SUPER_ADMIN must not have organizationId");
        }
        if (role.isTenantRole() && organizationId == null) {
            throw new IllegalArgumentException("Role " + role + " requires organizationId");
        }
    }

    public Optional<OrganizationId> organizationIdOpt() {
        return Optional.ofNullable(organizationId);
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

    public static User newOrgUser(UserId id, String email, String passwordHash,
                                  Role role, OrganizationId organizationId, UserId createdBy,
                                  UserStatus initialStatus, Instant now) {
        return new User(id, email, passwordHash, role, initialStatus,
                organizationId, createdBy, now, now, null);
    }

    public User withLastLoginAt(Instant when) {
        return new User(id, email, passwordHash, role, status, organizationId, createdBy,
                createdAt, when, when);
    }

    public User withStatus(UserStatus newStatus, Instant now) {
        return new User(id, email, passwordHash, role, newStatus, organizationId, createdBy,
                createdAt, now, lastLoginAt);
    }

    public User withPasswordHash(String newHash, Instant now) {
        return new User(id, email, newHash, role, status, organizationId, createdBy,
                createdAt, now, lastLoginAt);
    }
}
