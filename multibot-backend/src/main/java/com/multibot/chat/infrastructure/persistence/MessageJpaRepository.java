package com.multibot.chat.infrastructure.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface MessageJpaRepository extends JpaRepository<MessageJpaEntity, UUID> {

    @Query("SELECT m FROM MessageJpaEntity m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<MessageJpaEntity> findTopByDesc(@Param("sessionId") UUID sessionId, Pageable pageable);

    Page<MessageJpaEntity> findBySessionId(UUID sessionId, Pageable pageable);

    long countBySessionId(UUID sessionId);
}
