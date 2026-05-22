package com.multibot.tenancy.resolver;

import com.multibot.shared.tenant.TenantId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "multibot.tenancy.mode", havingValue = "mock", matchIfMissing = true)
public class MockTenantResolver implements TenantResolver {

    private static final TenantId DEFAULT_TENANT = TenantId.of(
            UUID.fromString("00000000-0000-7000-8000-000000000001"));

    @Override
    public Optional<TenantId> resolve(HttpServletRequest request) {
        return Optional.of(DEFAULT_TENANT);
    }
}
