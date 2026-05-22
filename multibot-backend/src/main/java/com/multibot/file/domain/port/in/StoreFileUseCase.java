package com.multibot.file.domain.port.in;

import com.multibot.file.domain.model.StoreFileCommand;
import com.multibot.file.domain.model.StoredFileId;

public interface StoreFileUseCase {

    StoredFileId store(StoreFileCommand command);
}
