package com.multibot.document.domain.port.in;

import com.multibot.document.domain.model.DocumentId;

public interface DeleteDocumentUseCase {

    void delete(DocumentId id);
}
