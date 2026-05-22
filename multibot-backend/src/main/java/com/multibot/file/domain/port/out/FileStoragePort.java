package com.multibot.file.domain.port.out;

public interface FileStoragePort {

    void write(String storageKey, byte[] bytes);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
