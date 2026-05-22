package com.multibot.tenant.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface TenantJpaRepository extends JpaRepository<TenantJpaEntity, UUID> {

    Optional<TenantJpaEntity> findBySlug(String slug);
}
