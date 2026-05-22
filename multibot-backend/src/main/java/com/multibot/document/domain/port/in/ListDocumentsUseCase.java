package com.multibot.document.domain.port.in;

import com.multibot.document.domain.model.DocumentSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListDocumentsUseCase {

    Page<DocumentSummary> list(Pageable pageable);
}
