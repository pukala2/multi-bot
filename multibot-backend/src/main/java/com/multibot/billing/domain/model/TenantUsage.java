package com.multibot.billing.domain.model;

import com.multibot.shared.tenant.TenantId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Wykorzystanie limitu per tenant per miesiac.
 * periodMonth = pierwszy dzien miesiaca (np. 2026-05-01).
 *
 * userMessages liczymy do limitu planu.
 * aiMessages / tokensUsed / costUsd to dane wewnetrzne (super admin / finance).
 */
public record TenantUsage(
        TenantId tenantId,
        LocalDate periodMonth,
        long userMessages,
        long aiMessages,
        long tokensUsed,
        BigDecimal costUsd
) {

    public TenantUsage {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(periodMonth, "periodMonth");
        Objects.requireNonNull(costUsd, "costUsd");
        if (periodMonth.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("periodMonth must be first day of month: " + periodMonth);
        }
    }

    public static TenantUsage empty(TenantId tenantId, LocalDate periodMonth) {
        return new TenantUsage(tenantId, periodMonth, 0, 0, 0, BigDecimal.ZERO);
    }
}
