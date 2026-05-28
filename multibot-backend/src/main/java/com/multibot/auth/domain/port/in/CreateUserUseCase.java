package com.multibot.auth.domain.port.in;

import com.multibot.auth.domain.model.User;
import com.multibot.shared.tenant.TenantId;

public interface CreateUserUseCase {

    /** Tworzy OWNERa dla tenanta z surowym haslem (hashowane w srodku). */
    User createOwner(TenantId tenantId, String email, String rawPassword);
}
