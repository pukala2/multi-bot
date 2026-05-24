package com.multibot.document.domain.port.in;

import com.multibot.document.domain.model.DocumentSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ListDocumentsUseCase {

    Page<DocumentSummary> list(Pageable pageable);

    Page<DocumentSummary> listByVersion(UUID versionId, Pageable pageable);
}
