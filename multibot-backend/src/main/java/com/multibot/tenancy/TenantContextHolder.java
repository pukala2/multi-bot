package com.multibot.tenancy;

import com.multibot.shared.tenant.TenantId;

import java.util.Optional;

final class TenantContextHolder {

    private static final ThreadLocal<TenantId> CURRENT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    static Optional<TenantId> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    static void set(TenantId tenantId) {
        CURRENT.set(tenantId);
    }

    static void clear() {
        CURRENT.remove();
    }
}
