package com.multibot.document.infrastructure.persistence;

import com.multibot.document.domain.model.Document;
import com.multibot.document.domain.model.DocumentId;
import com.multibot.document.domain.model.DocumentSummary;
import com.multibot.document.domain.port.out.DocumentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class DocumentRepositoryAdapter implements DocumentRepository {

    private final DocumentJpaRepository jpaRepository;

    DocumentRepositoryAdapter(DocumentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Document save(Document document) {
        DocumentJpaEntity entity = DocumentEntityMapper.toJpaEntity(document);
        DocumentJpaEntity saved = jpaRepository.save(entity);
        return DocumentEntityMapper.toDomain(saved);
    }

    @Override
    public Optional<Document> findById(DocumentId id) {
        return jpaRepository.findById(id.value())
                .map(DocumentEntityMapper::toDomain);
    }

    @Override
    public Page<DocumentSummary> findAllSummaries(Pageable pageable) {
        return jpaRepository.findAll(pageable)
                .map(DocumentEntityMapper::toSummary);
    }

    @Override
    public Page<DocumentSummary> findByVersion(UUID versionId, Pageable pageable) {
        return jpaRepository.findByVersionId(versionId, pageable)
                .map(DocumentEntityMapper::toSummary);
    }

    @Override
    public void deleteById(DocumentId id) {
        jpaRepository.deleteById(id.value());
    }
}
