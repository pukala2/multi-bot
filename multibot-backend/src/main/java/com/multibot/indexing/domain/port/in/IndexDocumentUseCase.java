package com.multibot.indexing.domain.port.in;

import com.multibot.document.domain.model.DocumentId;

public interface IndexDocumentUseCase {

    void index(DocumentId documentId);
}
