package com.multibot.tenancy;

import com.multibot.shared.tenant.TenantId;

import java.util.Optional;

public final class TenantContext {

    private TenantContext() {
    }

    public static TenantId get() {
        return tryGet().orElseThrow(() ->
                new IllegalStateException("TenantContext not set - request outside tenant scope"));
    }

    public static Optional<TenantId> tryGet() {
        return TenantContextHolder.get();
    }

    public static void set(TenantId tenantId) {
        TenantContextHolder.set(tenantId);
    }

    public static void clear() {
        TenantContextHolder.clear();
    }
}
