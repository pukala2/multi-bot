package com.multibot.tenant.domain.port.in;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.model.TenantSlug;

public interface GetTenantUseCase {

    Tenant getById(TenantId id);

    Tenant getBySlug(TenantSlug slug);
}
