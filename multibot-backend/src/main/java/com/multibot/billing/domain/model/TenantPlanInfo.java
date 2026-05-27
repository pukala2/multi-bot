package com.multibot.billing.domain.model;

import com.multibot.shared.tenant.TenantId;

/**
 * Snapshot z bazy: jaki plan ma tenant i ile efektywnie wiadomosci moze wyslac.
 * effectiveLimit uwzglednia override per-tenant (tenants.monthly_messages_limit_override).
 */
public record TenantPlanInfo(
        TenantId tenantId,
        BillingPlan plan,
        int effectiveLimit          // -1 = unlimited
) {
}
