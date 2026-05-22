package com.multibot.tenancy.hibernate;

import com.multibot.tenancy.TenantContext;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TenantIdResolver implements CurrentTenantIdentifierResolver<UUID> {

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        return TenantContext.tryGet()
                .map(tenantId -> tenantId.value())
                .orElse(null);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
