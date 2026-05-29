package com.multibot.widget.infrastructure.persistence;

import com.multibot.widget.domain.model.WidgetSettings;
import com.multibot.widget.domain.port.out.WidgetRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class WidgetJdbcRepository implements WidgetRepository {

    private final JdbcTemplate jdbc;

    WidgetJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WidgetSettings> findSettings(UUID tenantId) {
        try {
            WidgetSettings settings = jdbc.queryForObject(
                    """
                    SELECT accent_color, position, title, greeting, placeholder, is_active
                    FROM widget_settings
                    WHERE tenant_id = ?
                    """,
                    this::mapSettings, tenantId);
            return Optional.ofNullable(settings);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void insertSettings(UUID tenantId, WidgetSettings settings) {
        jdbc.update("""
                INSERT INTO widget_settings (tenant_id, accent_color, position, title, greeting, placeholder, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                tenantId, settings.accentColor(), settings.position(), settings.title(),
                settings.greeting(), settings.placeholder(), settings.isActive());
    }

    @Override
    public void updateSettings(UUID tenantId, WidgetSettings settings) {
        jdbc.update("""
                UPDATE widget_settings
                SET accent_color = ?, position = ?, title = ?, greeting = ?, placeholder = ?, is_active = ?, updated_at = NOW()
                WHERE tenant_id = ?
                """,
                settings.accentColor(), settings.position(), settings.title(),
                settings.greeting(), settings.placeholder(), settings.isActive(),
                tenantId);
    }

    @Override
    public Optional<String> findActiveKey(UUID tenantId) {
        try {
            String key = jdbc.queryForObject(
                    "SELECT key FROM widget_keys WHERE tenant_id = ? AND is_active = true LIMIT 1",
                    String.class, tenantId);
            return Optional.ofNullable(key);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void insertKey(UUID tenantId, String key) {
        jdbc.update("INSERT INTO widget_keys (key, tenant_id) VALUES (?, ?)", key, tenantId);
    }

    @Override
    public Optional<UUID> findTenantIdByActiveKey(String key) {
        try {
            UUID tenantId = jdbc.queryForObject(
                    "SELECT tenant_id FROM widget_keys WHERE key = ? AND is_active = true",
                    UUID.class, key);
            return Optional.ofNullable(tenantId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void touchKey(String key) {
        jdbc.update("UPDATE widget_keys SET last_used_at = NOW() WHERE key = ?", key);
    }

    @Override
    public List<String> findAllowedOrigins(String key) {
        try {
            String[] origins = jdbc.queryForObject(
                    "SELECT allowed_origins FROM widget_keys WHERE key = ? AND is_active = true",
                    (rs, rowNum) -> {
                        Array array = rs.getArray("allowed_origins");
                        return array == null ? new String[0] : (String[]) array.getArray();
                    },
                    key);
            return origins == null ? List.of() : Arrays.asList(origins);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }

    @Override
    public List<String> findAllowedOriginsByTenant(UUID tenantId) {
        try {
            String[] origins = jdbc.queryForObject(
                    "SELECT allowed_origins FROM widget_keys WHERE tenant_id = ? AND is_active = true LIMIT 1",
                    (rs, rowNum) -> {
                        Array array = rs.getArray("allowed_origins");
                        return array == null ? new String[0] : (String[]) array.getArray();
                    },
                    tenantId);
            return origins == null ? List.of() : Arrays.asList(origins);
        } catch (EmptyResultDataAccessException e) {
            return List.of();
        }
    }

    @Override
    public void updateAllowedOriginsByTenant(UUID tenantId, List<String> origins) {
        jdbc.update(con -> {
            var ps = con.prepareStatement(
                    "UPDATE widget_keys SET allowed_origins = ? WHERE tenant_id = ? AND is_active = true");
            ps.setArray(1, con.createArrayOf("text", origins.toArray()));
            ps.setObject(2, tenantId);
            return ps;
        });
    }

    private WidgetSettings mapSettings(ResultSet rs, int rowNum) throws SQLException {
        return new WidgetSettings(
                rs.getString("accent_color"),
                rs.getString("position"),
                rs.getString("title"),
                rs.getString("greeting"),
                rs.getString("placeholder"),
                rs.getBoolean("is_active"));
    }
}
