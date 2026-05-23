package com.multibot.indexing.domain.port.out;

import com.multibot.indexing.domain.model.ExtractedText;

public interface TextExtractor {

    ExtractedText extract(byte[] bytes, String declaredMimeType, String filename);
}
