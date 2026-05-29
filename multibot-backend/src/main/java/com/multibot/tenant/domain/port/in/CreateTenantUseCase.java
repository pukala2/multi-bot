package com.multibot.tenant.domain.port.in;

import com.multibot.shared.organization.OrganizationId;
import com.multibot.tenant.domain.model.Tenant;

public interface CreateTenantUseCase {

    /**
     * Tworzy nowego bota (tenanta) w obrebie organizacji. Slug generowany z nazwy (unikalny).
     */
    Tenant create(OrganizationId organizationId, String name);
}
