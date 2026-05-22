package com.multibot.file.domain.port.in;

import com.multibot.file.domain.model.StoredFileId;

public interface DeleteFileUseCase {

    void delete(StoredFileId id);
}
