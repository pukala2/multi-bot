package com.multibot.organization.domain.port.in;

import com.multibot.organization.domain.model.Organization;
import com.multibot.shared.organization.OrganizationId;

public interface GetOrganizationUseCase {

    Organization getById(OrganizationId id);
}
