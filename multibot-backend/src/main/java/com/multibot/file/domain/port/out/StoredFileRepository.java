package com.multibot.file.domain.port.out;

import com.multibot.file.domain.model.StoredFile;
import com.multibot.file.domain.model.StoredFileId;

import java.util.Optional;

public interface StoredFileRepository {

    StoredFile save(StoredFile file);

    Optional<StoredFile> findById(StoredFileId id);

    void deleteById(StoredFileId id);
}
