package com.multibot.organization.infrastructure.persistence;

import com.multibot.organization.domain.model.Organization;
import com.multibot.organization.domain.model.OrganizationSlug;
import com.multibot.organization.domain.port.out.OrganizationRepository;
import com.multibot.shared.organization.OrganizationId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class OrganizationRepositoryAdapter implements OrganizationRepository {

    private final OrganizationJpaRepository jpaRepository;

    OrganizationRepositoryAdapter(OrganizationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Organization> findById(OrganizationId id) {
        return jpaRepository.findById(id.value())
                .map(OrganizationEntityMapper::toDomain);
    }

    @Override
    public List<Organization> findAll() {
        return jpaRepository.findAll().stream()
                .map(OrganizationEntityMapper::toDomain)
                .toList();
    }

    @Override
    public Organization save(Organization organization) {
        jpaRepository.save(OrganizationEntityMapper.toJpaEntity(organization));
        return organization;
    }

    @Override
    public boolean existsBySlug(OrganizationSlug slug) {
        return jpaRepository.findBySlug(slug.value()).isPresent();
    }
}
