package com.multibot.indexing.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface IndexingJobJpaRepository extends JpaRepository<IndexingJobJpaEntity, UUID> {

    @Modifying
    @Query("DELETE FROM IndexingJobJpaEntity j WHERE j.documentId = :documentId")
    void deleteByDocumentId(@Param("documentId") UUID documentId);
}
