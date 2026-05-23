package com.multibot.chat.domain.exception;

import com.multibot.chat.domain.model.ChatSessionId;
import com.multibot.shared.exception.NotFoundException;

public final class ChatSessionNotFoundException extends NotFoundException {

    public ChatSessionNotFoundException(ChatSessionId id) {
        super("Chat session not found: id=" + id);
    }
}
