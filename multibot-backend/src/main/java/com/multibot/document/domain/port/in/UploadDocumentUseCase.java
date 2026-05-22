package com.multibot.document.domain.port.in;

import com.multibot.document.domain.model.Document;
import com.multibot.document.domain.model.UploadDocumentCommand;

public interface UploadDocumentUseCase {

    Document upload(UploadDocumentCommand command);
}
