package com.multibot.indexing.infrastructure.persistence;

import com.multibot.document.domain.model.DocumentId;
import com.multibot.indexing.domain.model.IndexingJob;
import com.multibot.indexing.domain.port.out.IndexingJobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class IndexingJobRepositoryAdapter implements IndexingJobRepository {

    private final IndexingJobJpaRepository jpaRepository;

    IndexingJobRepositoryAdapter(IndexingJobJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public IndexingJob save(IndexingJob job) {
        IndexingJobJpaEntity entity = IndexingJobEntityMapper.toJpaEntity(job);
        IndexingJobJpaEntity saved = jpaRepository.save(entity);
        return IndexingJobEntityMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public void deleteByDocumentId(DocumentId documentId) {
        jpaRepository.deleteByDocumentId(documentId.value());
    }
}
