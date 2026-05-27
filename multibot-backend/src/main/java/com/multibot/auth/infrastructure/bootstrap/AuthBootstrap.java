package com.multibot.auth.infrastructure.bootstrap;

import com.multibot.auth.domain.model.Role;
import com.multibot.auth.domain.model.User;
import com.multibot.auth.domain.model.UserId;
import com.multibot.auth.domain.model.UserStatus;
import com.multibot.auth.domain.port.out.UserRepository;
import com.multibot.shared.tenant.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Tworzy pierwszego super admina i (opcjonalnie) OWNERa dla Default Tenant na starcie aplikacji.
 * Idempotentny — sprawdza po emailu czy uzytkownik juz istnieje.
 * Wartosci z env vars przez {@link AuthBootstrapProperties} (multibot.bootstrap.*).
 */
@Component
@EnableConfigurationProperties(AuthBootstrapProperties.class)
class AuthBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AuthBootstrap.class);
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final AuthBootstrapProperties props;

    AuthBootstrap(UserRepository userRepository,
                  PasswordEncoder passwordEncoder,
                  JdbcTemplate jdbc,
                  AuthBootstrapProperties props) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    void bootstrap() {
        bootstrapSuperAdmin();
        if (props.defaultTenant() != null && props.defaultTenant().enabled()) {
            bootstrapDefaultTenantOwner();
        }
    }

    private void bootstrapSuperAdmin() {
        if (props.superadmin() == null) return;
        String email = props.superadmin().email();
        if (email == null || email.isBlank()) return;

        if (userRepository.existsByEmail(email)) {
            log.info("Super admin '{}' already exists, skipping bootstrap", email);
            return;
        }

        Instant now = Instant.now();
        User superAdmin = User.newSuperAdmin(
                UserId.generate(),
                email,
                passwordEncoder.encode(props.superadmin().password()),
                now);
        userRepository.save(superAdmin);
        log.warn("============================================================");
        log.warn("BOOTSTRAP: created SUPER_ADMIN '{}' (password from env/config)", email);
        log.warn("============================================================");
    }

    private void bootstrapDefaultTenantOwner() {
        // Sprawdz czy Default Tenant istnieje (z migracji V20260522_0101)
        Integer tenantExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, DEFAULT_TENANT_ID);
        if (tenantExists == null || tenantExists == 0) {
            log.info("Default Tenant not found in DB, skipping demo owner bootstrap");
            return;
        }

        String email = props.defaultTenant().ownerEmail();
        if (email == null || email.isBlank()) return;

        if (userRepository.existsByEmail(email)) {
            log.info("Demo owner '{}' already exists, skipping bootstrap", email);
            return;
        }

        Instant now = Instant.now();
        User owner = User.newTenantUser(
                UserId.generate(),
                email,
                passwordEncoder.encode(props.defaultTenant().ownerPassword()),
                Role.OWNER,
                TenantId.of(DEFAULT_TENANT_ID),
                findFirstSuperAdminId().orElseThrow(),
                UserStatus.ACTIVE,
                now);
        userRepository.save(owner);
        log.warn("BOOTSTRAP: created OWNER '{}' for Default Tenant", email);
    }

    private java.util.Optional<UserId> findFirstSuperAdminId() {
        return jdbc.query("SELECT id FROM users WHERE role = 'SUPER_ADMIN' ORDER BY created_at ASC LIMIT 1",
                        (rs, rn) -> UserId.of(rs.getObject("id", UUID.class)))
                .stream().findFirst();
    }
}
