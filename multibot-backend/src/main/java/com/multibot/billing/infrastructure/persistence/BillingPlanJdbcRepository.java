package com.multibot.billing.infrastructure.persistence;

import com.multibot.billing.domain.model.BillingPlan;
import com.multibot.billing.domain.model.TenantPlanInfo;
import com.multibot.billing.domain.port.out.BillingPlanRepository;
import com.multibot.shared.tenant.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
class BillingPlanJdbcRepository implements BillingPlanRepository {

    private final JdbcTemplate jdbc;

    BillingPlanJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BillingPlan> findByCode(String code) {
        List<BillingPlan> result = jdbc.query(
                "SELECT code, name, monthly_messages_limit, custom_domain_allowed, " +
                        "api_access_allowed, price_pln_monthly, display_order, active " +
                        "FROM billing_plans WHERE code = ? LIMIT 1",
                planMapper(), code);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    @Override
    public List<BillingPlan> findAllActive() {
        return jdbc.query(
                "SELECT code, name, monthly_messages_limit, custom_domain_allowed, " +
                        "api_access_allowed, price_pln_monthly, display_order, active " +
                        "FROM billing_plans WHERE active = TRUE ORDER BY display_order",
                planMapper());
    }

    @Override
    public Optional<TenantPlanInfo> findTenantPlanInfo(TenantId tenantId) {
        List<TenantPlanInfo> result = jdbc.query("""
                SELECT
                  bp.code, bp.name, bp.monthly_messages_limit, bp.custom_domain_allowed,
                  bp.api_access_allowed, bp.price_pln_monthly, bp.display_order, bp.active,
                  COALESCE(t.monthly_messages_limit_override, bp.monthly_messages_limit) AS effective_limit
                FROM tenants t
                JOIN billing_plans bp ON bp.code = t.plan
                WHERE t.id = ?
                LIMIT 1
                """,
                (rs, rowNum) -> {
                    BillingPlan plan = mapPlan(rs);
                    int effective = rs.getInt("effective_limit");
                    return new TenantPlanInfo(tenantId, plan, effective);
                },
                tenantId.value());
        return result.isEmpty() ? Optional.empty() : Optional.of(result.getFirst());
    }

    private RowMapper<BillingPlan> planMapper() {
        return (rs, rowNum) -> mapPlan(rs);
    }

    private static BillingPlan mapPlan(ResultSet rs) throws SQLException {
        Integer price = rs.getObject("price_pln_monthly", Integer.class);
        return new BillingPlan(
                rs.getString("code"),
                rs.getString("name"),
                rs.getInt("monthly_messages_limit"),
                rs.getBoolean("custom_domain_allowed"),
                rs.getBoolean("api_access_allowed"),
                price,
                rs.getInt("display_order"),
                rs.getBoolean("active"));
    }
}
