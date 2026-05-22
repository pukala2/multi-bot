package com.multibot.file.domain.port.in;

import com.multibot.file.domain.model.StoredFile;
import com.multibot.file.domain.model.StoredFileId;

public interface GetFileMetadataUseCase {

    StoredFile getMetadata(StoredFileId id);
}
