package com.multibot.chat.domain.port.in;

import com.multibot.chat.domain.model.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListSessionsUseCase {

    Page<ChatSession> list(Pageable pageable);

    long countMessages(com.multibot.chat.domain.model.ChatSessionId sessionId);
}
