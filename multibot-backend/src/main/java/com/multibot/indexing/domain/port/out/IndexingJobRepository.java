package com.multibot.indexing.domain.port.out;

import com.multibot.document.domain.model.DocumentId;
import com.multibot.indexing.domain.model.IndexingJob;

public interface IndexingJobRepository {

    IndexingJob save(IndexingJob job);

    void deleteByDocumentId(DocumentId documentId);
}
