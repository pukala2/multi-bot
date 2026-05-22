package com.multibot.tenancy.resolver;

import com.multibot.shared.tenant.TenantId;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

public interface TenantResolver {

    Optional<TenantId> resolve(HttpServletRequest request);
}
