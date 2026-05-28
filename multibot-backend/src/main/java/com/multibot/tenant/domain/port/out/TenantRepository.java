package com.multibot.tenant.domain.port.out;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.model.TenantSlug;

import java.util.List;
import java.util.Optional;

public interface TenantRepository {

    Optional<Tenant> findById(TenantId id);

    Optional<Tenant> findBySlug(TenantSlug slug);

    List<Tenant> findAll();

    Tenant save(Tenant tenant);

    boolean existsBySlug(TenantSlug slug);
}
