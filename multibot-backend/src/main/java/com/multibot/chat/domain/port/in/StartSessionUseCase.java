package com.multibot.chat.domain.port.in;

import com.multibot.chat.domain.model.ChatSession;
import com.multibot.chat.domain.model.StartSessionCommand;

public interface StartSessionUseCase {

    ChatSession start(StartSessionCommand command);
}
