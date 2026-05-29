package com.multibot.auth.infrastructure.persistence;

import com.multibot.auth.domain.model.Role;
import com.multibot.auth.domain.model.User;
import com.multibot.auth.domain.model.UserId;
import com.multibot.auth.domain.model.UserStatus;
import com.multibot.auth.domain.port.out.UserRepository;
import com.multibot.shared.organization.OrganizationId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
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
                "SELECT id, email, password_hash, role, status, organization_id, created_by, " +
                        "created_at, updated_at, last_login_at " +
                        "FROM users WHERE lower(email) = lower(?) LIMIT 1",
                rowMapper(), email);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    @Override
    public Optional<User> findById(UserId id) {
        List<User> result = jdbc.query(
                "SELECT id, email, password_hash, role, status, organization_id, created_by, " +
                        "created_at, updated_at, last_login_at " +
                        "FROM users WHERE id = ? LIMIT 1",
                rowMapper(), id.value());
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    @Override
    public User save(User user) {
        UUID orgUuid = user.organizationId() == null ? null : user.organizationId().value();
        UUID createdByUuid = user.createdBy() == null ? null : user.createdBy().value();
        Timestamp lastLoginTs = user.lastLoginAt() == null ? null : Timestamp.from(user.lastLoginAt());

        int updated = jdbc.update(
                "UPDATE users SET email = ?, password_hash = ?, role = ?, status = ?, " +
                        "organization_id = ?, created_by = ?, updated_at = ?, last_login_at = ? " +
                        "WHERE id = ?",
                user.email(),
                user.passwordHash(),
                user.role().name(),
                user.status().name(),
                orgUuid,
                createdByUuid,
                Timestamp.from(user.updatedAt()),
                lastLoginTs,
                user.id().value());

        if (updated == 0) {
            jdbc.update(
                    "INSERT INTO users (id, email, password_hash, role, status, organization_id, " +
                            "created_by, created_at, updated_at, last_login_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    user.id().value(),
                    user.email(),
                    user.passwordHash(),
                    user.role().name(),
                    user.status().name(),
                    orgUuid,
                    createdByUuid,
                    Timestamp.from(user.createdAt()),
                    Timestamp.from(user.updatedAt()),
                    lastLoginTs);
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
            UUID orgUuid = rs.getObject("organization_id", UUID.class);
            UUID createdByUuid = rs.getObject("created_by", UUID.class);
            Timestamp lastLogin = rs.getTimestamp("last_login_at");
            return new User(
                    UserId.of(rs.getObject("id", UUID.class)),
                    rs.getString("email"),
                    rs.getString("password_hash"),
                    Role.valueOf(rs.getString("role")),
                    UserStatus.valueOf(rs.getString("status")),
                    orgUuid == null ? null : OrganizationId.of(orgUuid),
                    createdByUuid == null ? null : UserId.of(createdByUuid),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant(),
                    lastLogin == null ? null : lastLogin.toInstant());
        };
    }
}
