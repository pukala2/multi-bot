package com.multibot.chat.domain.model;

import java.util.Map;
import java.util.Optional;

public record StartSessionCommand(
        Optional<String> anonymousId,
        Optional<String> externalId,
        Map<String, Object> metadata
) {

    public StartSessionCommand {
        if (anonymousId == null) anonymousId = Optional.empty();
        if (externalId == null) externalId = Optional.empty();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
