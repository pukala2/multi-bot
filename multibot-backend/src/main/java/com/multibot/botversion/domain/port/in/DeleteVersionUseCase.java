package com.multibot.botversion.domain.port.in;

import java.util.UUID;

public interface DeleteVersionUseCase {

    void delete(UUID versionId);
}
