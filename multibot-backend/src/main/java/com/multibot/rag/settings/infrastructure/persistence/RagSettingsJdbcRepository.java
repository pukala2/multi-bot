package com.multibot.rag.settings.infrastructure.persistence;

import com.multibot.rag.settings.domain.model.RagSettings;
import com.multibot.rag.settings.domain.port.out.RagSettingsRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

@Repository
class RagSettingsJdbcRepository implements RagSettingsRepository {

    private final JdbcTemplate jdbc;

    RagSettingsJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<RagSettings> find(UUID tenantId) {
        try {
            RagSettings result = jdbc.queryForObject(
                    "SELECT min_score, top_k, temperature, system_prompt FROM tenant_rag_settings WHERE tenant_id = ?",
                    this::mapRow, tenantId);
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void insert(UUID tenantId, RagSettings settings) {
        jdbc.update("""
                INSERT INTO tenant_rag_settings (tenant_id, min_score, top_k, temperature, system_prompt)
                VALUES (?, ?, ?, ?, ?)
                """,
                tenantId, settings.minScore(), settings.topK(), settings.temperature(),
                settings.systemPrompt().orElse(null));
    }

    @Override
    public void update(UUID tenantId, RagSettings settings) {
        jdbc.update("""
                UPDATE tenant_rag_settings
                SET min_score = ?, top_k = ?, temperature = ?, system_prompt = ?, updated_at = NOW()
                WHERE tenant_id = ?
                """,
                settings.minScore(), settings.topK(), settings.temperature(),
                settings.systemPrompt().orElse(null), tenantId);
    }

    private RagSettings mapRow(ResultSet rs, int rowNum) throws SQLException {
        String prompt = rs.getString("system_prompt");
        return new RagSettings(
                rs.getDouble("min_score"),
                rs.getInt("top_k"),
                rs.getDouble("temperature"),
                Optional.ofNullable(prompt).filter(p -> !p.isBlank()));
    }
}
