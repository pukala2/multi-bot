package com.multibot.chat.domain.port.in;

import com.multibot.chat.domain.model.SendMessageCommand;
import com.multibot.chat.domain.model.StreamingChatResponse;

public interface StreamMessageUseCase {

    StreamingChatResponse stream(SendMessageCommand command);
}
