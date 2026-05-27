package com.multibot.auth.domain.model;

import com.multibot.shared.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record User(
        UserId id,
        String email,
        String passwordHash,
        Role role,
        UserStatus status,
        Optional<TenantId> tenantId,
        Optional<UserId> createdBy,
        Instant createdAt,
        Instant updatedAt,
        Optional<Instant> lastLoginAt
) {

    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        if (email.isBlank()) throw new IllegalArgumentException("email is blank");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(lastLoginAt, "lastLoginAt");

        if (role == Role.SUPER_ADMIN && tenantId.isPresent()) {
            throw new IllegalArgumentException("SUPER_ADMIN must not have tenantId");
        }
        if (role.isTenantRole() && tenantId.isEmpty()) {
            throw new IllegalArgumentException("Role " + role + " requires tenantId");
        }
    }

    public static User newSuperAdmin(UserId id, String email, String passwordHash, Instant now) {
        return new User(id, email, passwordHash, Role.SUPER_ADMIN, UserStatus.ACTIVE,
                Optional.empty(), Optional.empty(), now, now, Optional.empty());
    }

    public static User newTenantUser(UserId id, String email, String passwordHash,
                                     Role role, TenantId tenant, UserId createdBy,
                                     UserStatus initialStatus, Instant now) {
        return new User(id, email, passwordHash, role, initialStatus,
                Optional.of(tenant), Optional.of(createdBy), now, now, Optional.empty());
    }

    public User withLastLoginAt(Instant when) {
        return new User(id, email, passwordHash, role, status, tenantId, createdBy,
                createdAt, when, Optional.of(when));
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
