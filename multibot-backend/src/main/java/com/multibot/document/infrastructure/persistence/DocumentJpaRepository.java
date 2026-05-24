package com.multibot.document.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface DocumentJpaRepository extends JpaRepository<DocumentJpaEntity, UUID> {

    Page<DocumentJpaEntity> findByVersionId(UUID versionId, Pageable pageable);
}
