package com.multibot.chat.application;

import com.multibot.chat.domain.exception.ChatSessionNotFoundException;
import com.multibot.chat.domain.model.ChatSession;
import com.multibot.chat.domain.model.ChatSessionId;
import com.multibot.chat.domain.model.Citation;
import com.multibot.chat.domain.model.EndUserIdentity;
import com.multibot.chat.domain.model.Message;
import com.multibot.chat.domain.model.MessageRole;
import com.multibot.chat.domain.model.MessageId;
import com.multibot.chat.domain.model.SendMessageCommand;
import com.multibot.chat.domain.model.StartSessionCommand;
import com.multibot.chat.domain.model.StreamingChatResponse;
import com.multibot.chat.domain.port.in.GetSessionHistoryUseCase;
import com.multibot.chat.domain.port.in.ListSessionsUseCase;
import com.multibot.chat.domain.port.in.SendMessageUseCase;
import com.multibot.chat.domain.port.in.StartSessionUseCase;
import com.multibot.chat.domain.port.in.StreamMessageUseCase;
import com.multibot.chat.domain.port.out.ChatRepository;
import com.multibot.document.domain.model.DocumentId;
import com.multibot.document.domain.port.in.GetDocumentUseCase;
import com.multibot.rag.domain.model.RagAnswer;
import com.multibot.rag.domain.model.RagMessage;
import com.multibot.rag.domain.model.RagOptions;
import com.multibot.rag.domain.model.RagQuery;
import com.multibot.rag.domain.model.StreamingRagAnswer;
import com.multibot.rag.domain.port.in.AnswerQuestionUseCase;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
class ChatService implements StartSessionUseCase, SendMessageUseCase, StreamMessageUseCase,
        GetSessionHistoryUseCase, ListSessionsUseCase {

    private final ChatRepository repository;
    private final AnswerQuestionUseCase answerQuestionUseCase;
    private final GetDocumentUseCase getDocumentUseCase;
    private final int memoryWindowSize;

    ChatService(ChatRepository repository,
                AnswerQuestionUseCase answerQuestionUseCase,
                GetDocumentUseCase getDocumentUseCase,
                @Value("${multibot.chat.memory.window-size}") int memoryWindowSize) {
        this.repository = repository;
        this.answerQuestionUseCase = answerQuestionUseCase;
        this.getDocumentUseCase = getDocumentUseCase;
        this.memoryWindowSize = memoryWindowSize;
    }

    @Override
    @Transactional
    public ChatSession start(StartSessionCommand command) {
        TenantId tenant = TenantContext.get();
        EndUserIdentity endUser = command.externalId()
                .map(ext -> EndUserIdentity.withExternal(
                        command.anonymousId().orElseGet(() -> EndUserIdentity.anonymous().anonymousId()), ext))
                .orElseGet(() -> command.anonymousId()
                        .map(EndUserIdentity::anonymous)
                        .orElseGet(EndUserIdentity::anonymous));

        ChatSession session = ChatSession.startedNow(
                ChatSessionId.generate(), tenant, endUser, command.metadata(), Instant.now());
        return repository.saveSession(session);
    }

    @Override
    @Transactional
    public Message send(SendMessageCommand command) {
        TenantId tenant = TenantContext.get();
        ChatSession session = repository.findSessionById(command.sessionId())
                .orElseThrow(() -> new ChatSessionNotFoundException(command.sessionId()));

        Message userMessage = Message.userMessage(tenant, session.id(), command.content(), Instant.now());
        repository.saveMessage(userMessage);

        List<Message> recent = repository.findRecentMessages(session.id(), memoryWindowSize);
        List<RagMessage> history = recent.stream()
                .filter(m -> m.role() == MessageRole.USER || m.role() == MessageRole.ASSISTANT)
                .map(this::toRagMessage)
                .toList();

        RagAnswer answer = answerQuestionUseCase.answer(
                new RagQuery(command.content(), history, RagOptions.defaults()));

        List<Citation> citations = answer.citations().stream()
                .map(this::toCitation)
                .toList();

        Message assistantMessage = Message.assistantMessage(
                tenant, session.id(), answer.text(), citations, answer.tokensUsed(), Instant.now());
        Message saved = repository.saveMessage(assistantMessage);

        repository.saveSession(session.touchedAt(Instant.now()));

        return saved;
    }

    @Override
    public StreamingChatResponse stream(SendMessageCommand command) {
        TenantId tenant = TenantContext.get();
        ChatSession session = repository.findSessionById(command.sessionId())
                .orElseThrow(() -> new ChatSessionNotFoundException(command.sessionId()));

        Message userMessage = Message.userMessage(tenant, session.id(), command.content(), Instant.now());
        repository.saveMessage(userMessage);

        List<Message> recent = repository.findRecentMessages(session.id(), memoryWindowSize);
        List<RagMessage> history = recent.stream()
                .filter(m -> m.role() == MessageRole.USER || m.role() == MessageRole.ASSISTANT)
                .map(this::toRagMessage)
                .toList();

        StreamingRagAnswer ragAnswer = answerQuestionUseCase.streamAnswer(
                new RagQuery(command.content(), history, RagOptions.defaults()));

        List<Citation> citations = ragAnswer.citations().stream()
                .map(this::toCitation)
                .toList();

        MessageId assistantMessageId = MessageId.generate();

        java.util.function.BiConsumer<String, Integer> onComplete = (fullText, tokensUsed) -> {
            TenantContext.set(tenant);
            try {
                Instant now = Instant.now();
                Message assistantMessage = new Message(
                        assistantMessageId, tenant, session.id(), MessageRole.ASSISTANT,
                        fullText.isBlank() ? "(empty response)" : fullText,
                        citations, java.util.Optional.of(tokensUsed), now);
                repository.saveMessage(assistantMessage);
                repository.saveSession(session.touchedAt(now));
            } finally {
                TenantContext.clear();
            }
        };

        java.util.function.Consumer<Throwable> onError = error -> {
            TenantContext.set(tenant);
            try {
                Instant now = Instant.now();
                Message errorMessage = new Message(
                        assistantMessageId, tenant, session.id(), MessageRole.ASSISTANT,
                        "Error: " + error.getMessage(),
                        java.util.List.of(), java.util.Optional.empty(), now);
                repository.saveMessage(errorMessage);
            } finally {
                TenantContext.clear();
            }
        };

        return new StreamingChatResponse(assistantMessageId, citations,
                ragAnswer.tokens(), onComplete, onError);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Message> getHistory(ChatSessionId sessionId, Pageable pageable) {
        if (repository.findSessionById(sessionId).isEmpty()) {
            throw new ChatSessionNotFoundException(sessionId);
        }
        return repository.findHistory(sessionId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatSession> list(Pageable pageable) {
        return repository.findAllSessions(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long countMessages(ChatSessionId sessionId) {
        return repository.countMessages(sessionId);
    }

    private RagMessage toRagMessage(Message message) {
        RagMessage.Role role = message.role() == MessageRole.USER ? RagMessage.Role.USER : RagMessage.Role.ASSISTANT;
        return new RagMessage(role, message.content());
    }

    private Citation toCitation(com.multibot.rag.domain.model.RagCitation ragCitation) {
        String filename = lookupFilename(ragCitation.documentId());
        return new Citation(
                ragCitation.chunkId(),
                ragCitation.documentId(),
                filename,
                ragCitation.snippet(),
                ragCitation.score());
    }

    private String lookupFilename(java.util.UUID documentId) {
        try {
            return getDocumentUseCase.getById(DocumentId.of(documentId)).filename();
        } catch (RuntimeException e) {
            return "(unknown)";
        }
    }
}
