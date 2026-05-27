package com.multibot.auth.infrastructure.persistence;

import com.multibot.auth.domain.model.Role;
import com.multibot.auth.domain.model.User;
import com.multibot.auth.domain.model.UserId;
import com.multibot.auth.domain.model.UserStatus;
import com.multibot.auth.domain.port.out.UserRepository;
import com.multibot.shared.tenant.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class UserJdbcRepository implements UserRepository {

    private final JdbcTemplate jdbc;

    UserJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        List<User> result = jdbc.query(
                "SELECT id, email, password_hash, role, status, tenant_id, created_by, " +
                        "created_at, updated_at, last_login_at " +
                        "FROM users WHERE lower(email) = lower(?) LIMIT 1",
                rowMapper(), email);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    @Override
    public Optional<User> findById(UserId id) {
        List<User> result = jdbc.query(
                "SELECT id, email, password_hash, role, status, tenant_id, created_by, " +
                        "created_at, updated_at, last_login_at " +
                        "FROM users WHERE id = ? LIMIT 1",
                rowMapper(), id.value());
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    @Override
    public User save(User user) {
        int updated = jdbc.update(
                "UPDATE users SET email = ?, password_hash = ?, role = ?, status = ?, " +
                        "tenant_id = ?, created_by = ?, updated_at = ?, last_login_at = ? " +
                        "WHERE id = ?",
                user.email(),
                user.passwordHash(),
                user.role().name(),
                user.status().name(),
                user.tenantId().map(TenantId::value).orElse(null),
                user.createdBy().map(UserId::value).orElse(null),
                Timestamp.from(user.updatedAt()),
                user.lastLoginAt().map(Timestamp::from).orElse(null),
                user.id().value());

        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO users (id, email, password_hash, role, status, tenant_id, " +
                            "created_by, created_at, updated_at, last_login_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    user.id().value(),
                    user.email(),
                    user.passwordHash(),
                    user.role().name(),
                    user.status().name(),
                    user.tenantId().map(TenantId::value).orElse(null),
                    user.createdBy().map(UserId::value).orElse(null),
                    Timestamp.from(user.createdAt()),
                    Timestamp.from(user.updatedAt()),
                    user.lastLoginAt().map(Timestamp::from).orElse(null));
        }
        return user;
    }

    @Override
    public boolean existsByEmail(String email) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE lower(email) = lower(?)",
                Integer.class, email);
        return count != null && count > 0;
    }

    private RowMapper<User> rowMapper() {
        return (rs, rowNum) -> {
            UUID tenantUuid = rs.getObject("tenant_id", UUID.class);
            UUID createdByUuid = rs.getObject("created_by", UUID.class);
            Timestamp lastLogin = rs.getTimestamp("last_login_at");
            return new User(
                    UserId.of(rs.getObject("id", UUID.class)),
                    rs.getString("email"),
                    rs.getString("password_hash"),
                    Role.valueOf(rs.getString("role")),
                    UserStatus.valueOf(rs.getString("status")),
                    Optional.ofNullable(tenantUuid).map(TenantId::of),
                    Optional.ofNullable(createdByUuid).map(UserId::of),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    Optional.ofNullable(lastLogin).map(Timestamp::toInstant));
        };
    }
}
