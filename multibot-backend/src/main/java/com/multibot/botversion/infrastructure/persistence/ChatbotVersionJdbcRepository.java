package com.multibot.botversion.infrastructure.persistence;

import com.multibot.botversion.domain.model.ChatbotVersion;
import com.multibot.botversion.domain.model.ChatbotVersion.VersionStatus;
import com.multibot.botversion.domain.port.out.ChatbotVersionRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ChatbotVersionJdbcRepository implements ChatbotVersionRepository {

    private final JdbcTemplate jdbc;

    ChatbotVersionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ChatbotVersion> findAllByTenant(UUID tenantId) {
        return jdbc.query("""
                SELECT v.id, v.tenant_id, v.name, v.status, v.created_at, v.published_at,
                       COALESCE((SELECT COUNT(*) FROM documents d WHERE d.version_id = v.id), 0) AS doc_count
                FROM chatbot_versions v
                WHERE v.tenant_id = ?
                ORDER BY v.created_at DESC
                """, this::mapRow, tenantId);
    }

    @Override
    public Optional<ChatbotVersion> findById(UUID versionId, UUID tenantId) {
        try {
            ChatbotVersion v = jdbc.queryForObject("""
                    SELECT v.id, v.tenant_id, v.name, v.status, v.created_at, v.published_at,
                           COALESCE((SELECT COUNT(*) FROM documents d WHERE d.version_id = v.id), 0) AS doc_count
                    FROM chatbot_versions v
                    WHERE v.id = ? AND v.tenant_id = ?
                    """, this::mapRow, versionId, tenantId);
            return Optional.ofNullable(v);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<ChatbotVersion> findActive(UUID tenantId) {
        try {
            ChatbotVersion v = jdbc.queryForObject("""
                    SELECT v.id, v.tenant_id, v.name, v.status, v.created_at, v.published_at,
                           COALESCE((SELECT COUNT(*) FROM documents d WHERE d.version_id = v.id), 0) AS doc_count
                    FROM chatbot_versions v
                    JOIN chatbot_active_version cav ON cav.version_id = v.id
                    WHERE cav.tenant_id = ?
                    """, this::mapRow, tenantId);
            return Optional.ofNullable(v);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> findActiveVersionId(UUID tenantId) {
        try {
            UUID id = jdbc.queryForObject(
                    "SELECT version_id FROM chatbot_active_version WHERE tenant_id = ?",
                    UUID.class, tenantId);
            return Optional.ofNullable(id);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void insertDraft(UUID id, UUID tenantId, String name) {
        jdbc.update(
                "INSERT INTO chatbot_versions (id, tenant_id, name, status) VALUES (?, ?, ?, 'DRAFT')",
                id, tenantId, name);
    }

    @Override
    public void deepCopyVersion(UUID fromVersionId, UUID toVersionId) {
        List<UUID> oldDocIds = jdbc.queryForList(
                "SELECT id FROM documents WHERE version_id = ?", UUID.class, fromVersionId);

        for (UUID oldDocId : oldDocIds) {
            UUID newDocId = UUID.randomUUID();

            jdbc.update("""
                    INSERT INTO documents (id, tenant_id, version_id, stored_file_id, filename, mime_type, size_bytes,
                                           status, uploaded_at, indexed_at, error_message, created_at, updated_at)
                    SELECT ?, tenant_id, ?, stored_file_id, filename, mime_type, size_bytes,
                           status, uploaded_at, indexed_at, error_message, NOW(), NOW()
                    FROM documents WHERE id = ?
                    """, newDocId, toVersionId, oldDocId);

            jdbc.update("""
                    INSERT INTO chunks (id, tenant_id, version_id, document_id, chunk_index, content, embedding, metadata, created_at)
                    SELECT gen_random_uuid(), tenant_id, ?, ?, chunk_index, content, embedding, metadata, NOW()
                    FROM chunks WHERE document_id = ? AND version_id = ?
                    """, toVersionId, newDocId, oldDocId, fromVersionId);
        }
    }

    @Override
    public void deleteVersion(UUID versionId) {
        jdbc.update("DELETE FROM chatbot_versions WHERE id = ?", versionId);
    }

    @Override
    public void activate(UUID versionId, UUID tenantId) {
        jdbc.update("""
                UPDATE chatbot_versions SET status = 'ARCHIVED'
                WHERE tenant_id = ? AND status = 'ACTIVE'
                """, tenantId);

        jdbc.update("""
                UPDATE chatbot_versions SET status = 'ACTIVE', published_at = NOW()
                WHERE id = ? AND tenant_id = ?
                """, versionId, tenantId);

        jdbc.update("""
                INSERT INTO chatbot_active_version (tenant_id, version_id, activated_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (tenant_id) DO UPDATE
                SET version_id = EXCLUDED.version_id, activated_at = NOW()
                """, tenantId, versionId);
    }

    @Override
    public boolean isActive(UUID versionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM chatbot_active_version WHERE version_id = ?",
                Integer.class, versionId);
        return count != null && count > 0;
    }

    private ChatbotVersion mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp publishedTs = rs.getTimestamp("published_at");
        return new ChatbotVersion(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("tenant_id"),
                rs.getString("name"),
                VersionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                publishedTs == null ? Optional.empty() : Optional.of(publishedTs.toInstant()),
                rs.getLong("doc_count"));
    }
}
