package com.multibot.vector.domain.port.in;

import com.multibot.vector.domain.model.VectorSearchQuery;
import com.multibot.vector.domain.model.VectorSearchResult;

import java.util.List;

public interface SearchChunksUseCase {

    List<VectorSearchResult> search(VectorSearchQuery query);
}
