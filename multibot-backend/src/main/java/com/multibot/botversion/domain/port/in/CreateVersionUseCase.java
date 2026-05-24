package com.multibot.botversion.domain.port.in;

import com.multibot.botversion.domain.model.ChatbotVersion;

import java.util.UUID;

public interface CreateVersionUseCase {

    /**
     * @param copyFromVersionId nullable — jeśli null, tworzy pustą wersję;
     *                          jeśli set, robi deep-copy z tej wersji
     */
    ChatbotVersion create(String name, UUID copyFromVersionId);
}
