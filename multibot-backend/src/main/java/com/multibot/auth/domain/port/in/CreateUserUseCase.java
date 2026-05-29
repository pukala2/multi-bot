package com.multibot.auth.domain.port.in;

import com.multibot.auth.domain.model.User;
import com.multibot.shared.organization.OrganizationId;

public interface CreateUserUseCase {

    /** Tworzy OWNERa dla organizacji z surowym haslem (hashowane w srodku). */
    User createOwner(OrganizationId organizationId, String email, String rawPassword);
}
