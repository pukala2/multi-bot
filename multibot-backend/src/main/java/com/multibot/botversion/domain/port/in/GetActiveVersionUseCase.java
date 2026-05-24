package com.multibot.botversion.domain.port.in;

import com.multibot.botversion.domain.model.ChatbotVersion;

import java.util.Optional;
import java.util.UUID;

public interface GetActiveVersionUseCase {

    Optional<ChatbotVersion> getActive();

    Optional<UUID> getActiveVersionId();
}
