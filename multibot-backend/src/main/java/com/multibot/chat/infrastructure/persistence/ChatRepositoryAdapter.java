package com.multibot.chat.infrastructure.persistence;

import com.multibot.chat.domain.model.ChatSession;
import com.multibot.chat.domain.model.ChatSessionId;
import com.multibot.chat.domain.model.Message;
import com.multibot.chat.domain.port.out.ChatRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
class ChatRepositoryAdapter implements ChatRepository {

    private final ChatSessionJpaRepository sessionRepo;
    private final MessageJpaRepository messageRepo;

    ChatRepositoryAdapter(ChatSessionJpaRepository sessionRepo, MessageJpaRepository messageRepo) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
    }

    @Override
    public ChatSession saveSession(ChatSession session) {
        ChatSessionJpaEntity saved = sessionRepo.save(ChatEntityMapper.toJpaEntity(session));
        return ChatEntityMapper.toDomain(saved);
    }

    @Override
    public Optional<ChatSession> findSessionById(ChatSessionId id) {
        return sessionRepo.findById(id.value()).map(ChatEntityMapper::toDomain);
    }

    @Override
    public Page<ChatSession> findAllSessions(Pageable pageable) {
        return sessionRepo.findAll(pageable).map(ChatEntityMapper::toDomain);
    }

    @Override
    public Message saveMessage(Message message) {
        MessageJpaEntity saved = messageRepo.save(ChatEntityMapper.toJpaEntity(message));
        return ChatEntityMapper.toDomain(saved);
    }

    @Override
    public List<Message> findRecentMessages(ChatSessionId sessionId, int limit) {
        List<MessageJpaEntity> entities = messageRepo.findTopByDesc(sessionId.value(), PageRequest.of(0, limit));
        List<Message> messages = entities.stream().map(ChatEntityMapper::toDomain).toList();
        java.util.ArrayList<Message> reversed = new java.util.ArrayList<>(messages);
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    @Override
    public Page<Message> findHistory(ChatSessionId sessionId, Pageable pageable) {
        return messageRepo.findBySessionId(sessionId.value(), pageable).map(ChatEntityMapper::toDomain);
    }

    @Override
    public long countMessages(ChatSessionId sessionId) {
        return messageRepo.countBySessionId(sessionId.value());
    }
}
