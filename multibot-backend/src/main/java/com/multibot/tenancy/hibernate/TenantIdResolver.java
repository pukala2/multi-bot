package com.multibot.tenancy.hibernate;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantIdResolver implements CurrentTenantIdentifierResolver<UUID> {

    private static final UUID NO_TENANT = new UUID(0L, 0L);

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.tryGet()
                .map(TenantId::value)
                .orElse(NO_TENANT);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
