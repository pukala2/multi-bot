package com.multibot.billing.domain.model;

import java.util.Objects;

/**
 * Plan cenowy. Limity i features definiowane w bazie (tabela billing_plans).
 * monthlyMessagesLimit = -1 oznacza unlimited (Enterprise).
 */
public record BillingPlan(
        String code,
        String name,
        int monthlyMessagesLimit,
        Integer maxBots,           // null = bez limitu botow (Enterprise)
        boolean customDomainAllowed,
        boolean apiAccessAllowed,
        Integer pricePlnMonthly,   // null = custom pricing
        int displayOrder,
        boolean active
) {

    public BillingPlan {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }

    public boolean isUnlimited() {
        return monthlyMessagesLimit == -1;
    }
}
