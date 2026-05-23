package com.multibot.chat.domain.port.in;

import com.multibot.chat.domain.model.ChatSessionId;
import com.multibot.chat.domain.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GetSessionHistoryUseCase {

    Page<Message> getHistory(ChatSessionId sessionId, Pageable pageable);
}
