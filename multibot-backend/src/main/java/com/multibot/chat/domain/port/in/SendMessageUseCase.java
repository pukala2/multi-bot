package com.multibot.chat.domain.port.in;

import com.multibot.chat.domain.model.Message;
import com.multibot.chat.domain.model.SendMessageCommand;

public interface SendMessageUseCase {

    Message send(SendMessageCommand command);
}
