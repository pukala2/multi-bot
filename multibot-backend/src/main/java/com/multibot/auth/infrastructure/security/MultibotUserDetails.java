package com.multibot.auth.infrastructure.security;

import com.multibot.auth.domain.model.Role;
import com.multibot.auth.domain.model.User;
import com.multibot.auth.domain.model.UserId;
import com.multibot.auth.domain.model.UserStatus;
import com.multibot.shared.tenant.TenantId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapter naszego domain {@link User} do {@link UserDetails} Spring Security.
 * Trzymany w SecurityContext = trzymany w sesji (Spring Session JDBC serializuje go do BYTEA).
 * Stad pola sa flat-serializable (UUID, String, enum) — bez Optional bo Optional NIE implementuje Serializable.
 */
public final class MultibotUserDetails implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String email;
    private final String passwordHash;
    private final Role role;
    private final UserStatus status;
    private final UUID tenantId;        // nullable: null = SUPER_ADMIN

    public MultibotUserDetails(User user) {
        this.userId = user.id().value();
        this.email = user.email();
        this.passwordHash = user.passwordHash();
        this.role = user.role();
        this.status = user.status();
        this.tenantId = user.tenantId().map(TenantId::value).orElse(null);
    }

    public UserId userId() {
        return UserId.of(userId);
    }

    public Optional<TenantId> tenantIdAsOptional() {
        return Optional.ofNullable(tenantId).map(TenantId::of);
    }

    public Role role() {
        return role;
    }

    public UserStatus status() {
        return status;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.springAuthority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.DISABLED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status.canLogin();
    }
}
