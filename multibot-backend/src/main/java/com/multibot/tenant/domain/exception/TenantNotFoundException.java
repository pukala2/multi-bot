package com.multibot.tenant.domain.exception;

import com.multibot.shared.exception.NotFoundException;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenant.domain.model.TenantSlug;

public final class TenantNotFoundException extends NotFoundException {

    public TenantNotFoundException(TenantId id) {
        super("Tenant not found: id=" + id);
    }

    public TenantNotFoundException(TenantSlug slug) {
        super("Tenant not found: slug=" + slug);
    }
}
