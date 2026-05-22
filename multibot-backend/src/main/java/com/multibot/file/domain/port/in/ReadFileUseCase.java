package com.multibot.file.domain.port.in;

import com.multibot.file.domain.model.FileContent;
import com.multibot.file.domain.model.StoredFileId;

public interface ReadFileUseCase {

    FileContent read(StoredFileId id);
}
