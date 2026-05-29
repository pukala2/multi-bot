package com.multibot.organization.domain.exception;

import com.multibot.shared.exception.NotFoundException;
import com.multibot.shared.organization.OrganizationId;

public final class OrganizationNotFoundException extends NotFoundException {

    public OrganizationNotFoundException(OrganizationId id) {
        super("Organization not found: id=" + id);
    }
}
