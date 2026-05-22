package com.multibot.vector.domain.port.in;

import java.util.UUID;

public interface DeleteChunksByDocumentUseCase {

    void deleteByDocument(UUID documentId);
}
