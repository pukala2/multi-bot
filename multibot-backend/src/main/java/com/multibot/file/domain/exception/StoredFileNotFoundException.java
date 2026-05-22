package com.multibot.file.domain.exception;

import com.multibot.file.domain.model.StoredFileId;
import com.multibot.shared.exception.NotFoundException;

public final class StoredFileNotFoundException extends NotFoundException {

    public StoredFileNotFoundException(StoredFileId id) {
        super("Stored file not found: id=" + id);
    }
}
