package com.multibot.document.domain.port.out;

import com.multibot.document.domain.model.Document;
import com.multibot.document.domain.model.DocumentId;
import com.multibot.document.domain.model.DocumentSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository {

    Document save(Document document);

    Optional<Document> findById(DocumentId id);

    Page<DocumentSummary> findAllSummaries(Pageable pageable);

    Page<DocumentSummary> findByVersion(UUID versionId, Pageable pageable);

    void deleteById(DocumentId id);
}
