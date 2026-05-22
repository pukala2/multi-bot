package com.multibot.document.domain.port.in;

import com.multibot.document.domain.model.Document;
import com.multibot.document.domain.model.DocumentId;

public interface GetDocumentUseCase {

    Document getById(DocumentId id);
}
