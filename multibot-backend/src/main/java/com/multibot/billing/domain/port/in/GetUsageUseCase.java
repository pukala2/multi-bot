package com.multibot.billing.domain.port.in;

import com.multibot.billing.domain.model.BillingPlan;
import com.multibot.billing.domain.model.TenantUsage;
import com.multibot.shared.tenant.TenantId;

public interface GetUsageUseCase {

    /** Aktualny usage tenanta w biezacym miesiacu (zwraca empty jak nie ma jeszcze wiersza). */
    TenantUsage getCurrentUsage(TenantId tenantId);

    /** Plan przypisany tenantowi. */
    BillingPlan getPlan(TenantId tenantId);

    /** Efektywny limit = override > 0 jezeli ustawiony, inaczej z planu. */
    int getEffectiveLimit(TenantId tenantId);
}
