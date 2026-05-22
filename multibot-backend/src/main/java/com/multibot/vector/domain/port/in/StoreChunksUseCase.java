package com.multibot.vector.domain.port.in;

import com.multibot.vector.domain.model.Chunk;

import java.util.List;

public interface StoreChunksUseCase {

    void storeAll(List<Chunk> chunks);
}
