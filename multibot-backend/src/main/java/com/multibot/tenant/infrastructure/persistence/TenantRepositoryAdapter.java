package com.multibot.tenant.infrastructure.persistence;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.model.TenantSlug;
import com.multibot.tenant.domain.port.out.TenantRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class TenantRepositoryAdapter implements TenantRepository {

    private final TenantJpaRepository jpaRepository;

    TenantRepositoryAdapter(TenantJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Tenant> findById(TenantId id) {
        return jpaRepository.findById(id.value())
                .map(TenantEntityMapper::toDomain);
    }

    @Override
    public Optional<Tenant> findBySlug(TenantSlug slug) {
        return jpaRepository.findBySlug(slug.value())
                .map(TenantEntityMapper::toDomain);
    }

    @Override
    public List<Tenant> findAll() {
        return jpaRepository.findAll().stream()
                .map(TenantEntityMapper::toDomain)
                .toList();
    }

    @Override
    public Tenant save(Tenant tenant) {
        jpaRepository.save(TenantEntityMapper.toJpaEntity(tenant));
        return tenant;
    }

    @Override
    public boolean existsBySlug(TenantSlug slug) {
        return jpaRepository.findBySlug(slug.value()).isPresent();
    }
}
