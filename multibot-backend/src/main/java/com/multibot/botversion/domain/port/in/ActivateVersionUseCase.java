package com.multibot.botversion.domain.port.in;

import com.multibot.botversion.domain.model.ChatbotVersion;

import java.util.UUID;

public interface ActivateVersionUseCase {

    ChatbotVersion activate(UUID versionId);
}
