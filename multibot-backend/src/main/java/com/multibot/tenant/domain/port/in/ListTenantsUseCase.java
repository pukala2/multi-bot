package com.multibot.tenant.domain.port.in;

import com.multibot.shared.organization.OrganizationId;
import com.multibot.tenant.domain.model.Tenant;

import java.util.List;

public interface ListTenantsUseCase {

    List<Tenant> listAll();

    /** Boty nalezace do danej organizacji. */
    List<Tenant> listByOrganization(OrganizationId organizationId);
}
