package com.multibot.tenant.domain.port.in;

import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.model.TenantPlan;

public interface CreateTenantUseCase {

    /**
     * Tworzy nowego tenanta. Slug generowany z nazwy (unikalny).
     * companyName + nip opcjonalne (nullable).
     */
    Tenant create(String name, TenantPlan plan, String companyName, String nip);
}
