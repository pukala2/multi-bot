package com.multibot.chat.domain.port.out;

import com.multibot.chat.domain.model.ChatSession;
import com.multibot.chat.domain.model.ChatSessionId;
import com.multibot.chat.domain.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ChatRepository {

    ChatSession saveSession(ChatSession session);

    Optional<ChatSession> findSessionById(ChatSessionId id);

    Page<ChatSession> findAllSessions(Pageable pageable);

    Message saveMessage(Message message);

    List<Message> findRecentMessages(ChatSessionId sessionId, int limit);

    Page<Message> findHistory(ChatSessionId sessionId, Pageable pageable);

    long countMessages(ChatSessionId sessionId);
}
