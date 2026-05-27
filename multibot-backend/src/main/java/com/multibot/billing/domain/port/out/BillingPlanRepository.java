package com.multibot.billing.domain.port.out;

import com.multibot.billing.domain.model.BillingPlan;
import com.multibot.billing.domain.model.TenantPlanInfo;
import com.multibot.shared.tenant.TenantId;

import java.util.List;
import java.util.Optional;

public interface BillingPlanRepository {

    Optional<BillingPlan> findByCode(String code);

    List<BillingPlan> findAllActive();

    /** Plan + efektywny limit dla tenanta (z uwzglednieniem override). */
    Optional<TenantPlanInfo> findTenantPlanInfo(TenantId tenantId);
}
