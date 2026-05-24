package com.multibot.botversion.domain.port.in;

import com.multibot.botversion.domain.model.ChatbotVersion;

import java.util.List;

public interface ListVersionsUseCase {

    List<ChatbotVersion> list();
}
