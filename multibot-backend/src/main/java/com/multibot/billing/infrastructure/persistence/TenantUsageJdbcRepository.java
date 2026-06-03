package com.multibot.billing.infrastructure.persistence;

import com.multibot.billing.domain.model.OrgTokenCost;
import com.multibot.billing.domain.model.TenantUsage;
import com.multibot.billing.domain.port.out.TenantUsageRepository;
import com.multibot.shared.tenant.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
class TenantUsageJdbcRepository implements TenantUsageRepository {

    private final JdbcTemplate jdbc;

    TenantUsageJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TenantUsage findCurrent(TenantId tenantId, LocalDate periodMonth) {
        List<TenantUsage> result = jdbc.query(
                "SELECT tenant_id, period_month, user_messages, ai_messages, tokens_used, cost_usd " +
                        "FROM tenant_usage WHERE tenant_id = ? AND period_month = ?",
                (rs, rowNum) -> new TenantUsage(
                        TenantId.of(rs.getObject("tenant_id", java.util.UUID.class)),
                        rs.getDate("period_month").toLocalDate(),
                        rs.getLong("user_messages"),
                        rs.getLong("ai_messages"),
                        rs.getLong("tokens_used"),
                        rs.getBigDecimal("cost_usd")),
                tenantId.value(), Date.valueOf(periodMonth));

        return result.isEmpty()
                ? TenantUsage.empty(tenantId, periodMonth)
                : result.getFirst();
    }

    @Override
    public long sumUserMessagesForOrgOf(TenantId tenantId, LocalDate periodMonth) {
        Long sum = jdbc.queryForObject("""
                SELECT COALESCE(SUM(tu.user_messages), 0)
                FROM tenants sibling
                LEFT JOIN tenant_usage tu
                       ON tu.tenant_id = sibling.id AND tu.period_month = ?
                WHERE sibling.organization_id = (SELECT organization_id FROM tenants WHERE id = ?)
                """,
                Long.class,
                Date.valueOf(periodMonth), tenantId.value());
        return sum == null ? 0L : sum;
    }

    @Override
    public long incrementUserMessages(TenantId tenantId, LocalDate periodMonth) {
        // UPSERT z atomowym +1, RETURNING daje nowa wartosc
        Long newValue = jdbc.queryForObject("""
                INSERT INTO tenant_usage (tenant_id, period_month, user_messages, created_at, updated_at)
                VALUES (?, ?, 1, now(), now())
                ON CONFLICT (tenant_id, period_month) DO UPDATE
                SET user_messages = tenant_usage.user_messages + 1,
                    updated_at = now()
                RETURNING user_messages
                """,
                Long.class,
                tenantId.value(), Date.valueOf(periodMonth));
        return newValue == null ? 0L : newValue;
    }

    @Override
    public BigDecimal sumAllChatCost() {
        BigDecimal sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(cost_usd), 0) FROM tenant_usage", BigDecimal.class);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public BigDecimal sumAllEmbeddingCost() {
        BigDecimal sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(embedding_cost), 0) FROM tenant_usage", BigDecimal.class);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public void incrementEmbeddingUsage(TenantId tenantId, LocalDate periodMonth, long tokens, BigDecimal cost) {
        jdbc.update("""
                INSERT INTO tenant_usage (tenant_id, period_month, embedding_tokens, embedding_cost, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                ON CONFLICT (tenant_id, period_month) DO UPDATE
                SET embedding_tokens = tenant_usage.embedding_tokens + EXCLUDED.embedding_tokens,
                    embedding_cost = tenant_usage.embedding_cost + EXCLUDED.embedding_cost,
                    updated_at = now()
                """,
                tenantId.value(), Date.valueOf(periodMonth), tokens, cost);
    }

    @Override
    public OrgTokenCost sumTokensCostForOrgOf(TenantId tenantId, LocalDate periodMonth) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(tu.tokens_used + tu.embedding_tokens), 0) AS toks,
                       COALESCE(SUM(tu.cost_usd + tu.embedding_cost), 0) AS cost
                FROM tenants sibling
                LEFT JOIN tenant_usage tu
                       ON tu.tenant_id = sibling.id AND tu.period_month = ?
                WHERE sibling.organization_id = (SELECT organization_id FROM tenants WHERE id = ?)
                """,
                (rs, rowNum) -> new OrgTokenCost(rs.getLong("toks"), rs.getBigDecimal("cost")),
                Date.valueOf(periodMonth), tenantId.value());
    }

    @Override
    public void incrementAiMessages(TenantId tenantId, LocalDate periodMonth, long tokensUsed, BigDecimal costUsd) {
        jdbc.update("""
                INSERT INTO tenant_usage (tenant_id, period_month, ai_messages, tokens_used, cost_usd, created_at, updated_at)
                VALUES (?, ?, 1, ?, ?, now(), now())
                ON CONFLICT (tenant_id, period_month) DO UPDATE
                SET ai_messages = tenant_usage.ai_messages + 1,
                    tokens_used = tenant_usage.tokens_used + EXCLUDED.tokens_used,
                    cost_usd = tenant_usage.cost_usd + EXCLUDED.cost_usd,
                    updated_at = now()
                """,
                tenantId.value(), Date.valueOf(periodMonth), tokensUsed, costUsd);
    }
}
