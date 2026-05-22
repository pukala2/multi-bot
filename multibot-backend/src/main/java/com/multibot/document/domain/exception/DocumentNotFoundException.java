package com.multibot.document.domain.exception;

import com.multibot.document.domain.model.DocumentId;
import com.multibot.shared.exception.NotFoundException;

public final class DocumentNotFoundException extends NotFoundException {

    public DocumentNotFoundException(DocumentId id) {
        super("Document not found: id=" + id);
    }
}
