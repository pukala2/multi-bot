package com.multibot.tenant.application;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenant.domain.exception.TenantNotFoundException;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.model.TenantSlug;
import com.multibot.tenant.domain.port.in.GetTenantUseCase;
import com.multibot.tenant.domain.port.out.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class TenantService implements GetTenantUseCase {

    private final TenantRepository tenantRepository;

    TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Tenant getById(TenantId id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id));
    }

    @Override
    public Tenant getBySlug(TenantSlug slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new TenantNotFoundException(slug));
    }
}
