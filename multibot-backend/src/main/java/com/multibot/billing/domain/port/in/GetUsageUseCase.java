package com.multibot.billing.domain.port.in;

import com.multibot.billing.domain.model.BillingPlan;
import com.multibot.billing.domain.model.OrgTokenCost;
import com.multibot.billing.domain.model.TenantUsage;
import com.multibot.shared.tenant.TenantId;

public interface GetUsageUseCase {

    /** Aktualny usage tego bota (tenanta) w biezacym miesiacu (zwraca empty jak nie ma wiersza). */
    TenantUsage getCurrentUsage(TenantId tenantId);

    /** Suma user_messages wszystkich botow organizacji w biezacym miesiacu — to liczy sie do limitu. */
    long getPooledUsedMessages(TenantId tenantId);

    /** Tokeny + koszt organizacji (suma botow) w biezacym miesiacu — diagnostyka super admina. */
    OrgTokenCost getOrgTokenCost(TenantId anyBotOfOrg);

    /** Plan przypisany organizacji bota. */
    BillingPlan getPlan(TenantId tenantId);

    /** Efektywny limit = override > 0 jezeli ustawiony, inaczej z planu. */
    int getEffectiveLimit(TenantId tenantId);
}
