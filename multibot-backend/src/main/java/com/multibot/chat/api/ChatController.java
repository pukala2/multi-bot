package com.multibot.chat.api;

import com.multibot.api.generated.api.ChatApi;
import com.multibot.api.generated.model.ChatSessionResponse;
import com.multibot.api.generated.model.MessageResponse;
import com.multibot.api.generated.model.PaginatedChatSessionResponse;
import com.multibot.api.generated.model.PaginatedMessageResponse;
import com.multibot.api.generated.model.SendMessageRequest;
import com.multibot.api.generated.model.StartSessionRequest;
import com.multibot.chat.domain.model.ChatSession;
import com.multibot.chat.domain.model.ChatSessionId;
import com.multibot.chat.domain.model.Message;
import com.multibot.chat.domain.model.SendMessageCommand;
import com.multibot.chat.domain.model.StartSessionCommand;
import com.multibot.chat.domain.model.StreamingChatResponse;
import com.multibot.chat.domain.port.in.GetSessionHistoryUseCase;
import com.multibot.chat.domain.port.in.ListSessionsUseCase;
import com.multibot.chat.domain.port.in.SendMessageUseCase;
import com.multibot.chat.domain.port.in.StartSessionUseCase;
import com.multibot.chat.domain.port.in.StreamMessageUseCase;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
class ChatController implements ChatApi {

    private static final long SSE_TIMEOUT_MS = 60_000L;

    private final StartSessionUseCase startSessionUseCase;
    private final SendMessageUseCase sendMessageUseCase;
    private final StreamMessageUseCase streamMessageUseCase;
    private final GetSessionHistoryUseCase getSessionHistoryUseCase;
    private final ListSessionsUseCase listSessionsUseCase;

    ChatController(StartSessionUseCase startSessionUseCase,
                   SendMessageUseCase sendMessageUseCase,
                   StreamMessageUseCase streamMessageUseCase,
                   GetSessionHistoryUseCase getSessionHistoryUseCase,
                   ListSessionsUseCase listSessionsUseCase) {
        this.startSessionUseCase = startSessionUseCase;
        this.sendMessageUseCase = sendMessageUseCase;
        this.streamMessageUseCase = streamMessageUseCase;
        this.getSessionHistoryUseCase = getSessionHistoryUseCase;
        this.listSessionsUseCase = listSessionsUseCase;
    }

    @Override
    public ResponseEntity<ChatSessionResponse> startChatSession(StartSessionRequest request) {
        StartSessionCommand command = toCommand(request);
        ChatSession session = startSessionUseCase.start(command);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(session.id().value())
                .toUri();

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .location(location)
                .body(ChatApiMapper.toResponse(session));
    }

    @Override
    public ResponseEntity<MessageResponse> sendMessage(UUID id, SendMessageRequest request) {
        Message message = sendMessageUseCase.send(
                new SendMessageCommand(ChatSessionId.of(id), request.getContent()));
        return ResponseEntity.ok(ChatApiMapper.toResponse(message));
    }

    @PostMapping(
            path = "/api/chat/sessions/{id}/messages",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public SseEmitter streamMessage(@PathVariable("id") UUID id, @Valid @RequestBody SendMessageRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        StreamingChatResponse streaming = streamMessageUseCase.stream(
                new SendMessageCommand(ChatSessionId.of(id), request.getContent()));

        StringBuilder fullText = new StringBuilder();
        AtomicInteger tokenCount = new AtomicInteger(0);

        streaming.tokens().subscribe(
                token -> sendChunk(emitter, fullText, tokenCount, token),
                error -> handleError(emitter, streaming, error),
                () -> completeStream(emitter, streaming, fullText, tokenCount));

        return emitter;
    }

    @Override
    public ResponseEntity<PaginatedMessageResponse> getSessionHistory(UUID id, Integer page, Integer size, String sort) {
        var pageable = ChatApiMapper.toPageable(page, size, sort, "createdAt", Sort.Direction.ASC);
        var result = getSessionHistoryUseCase.getHistory(ChatSessionId.of(id), pageable);
        return ResponseEntity.ok(ChatApiMapper.toPaginatedMessages(result));
    }

    @Override
    public ResponseEntity<PaginatedChatSessionResponse> listChatSessions(Integer page, Integer size, String sort) {
        var pageable = ChatApiMapper.toPageable(page, size, sort, "lastActivityAt", Sort.Direction.DESC);
        var result = listSessionsUseCase.list(pageable);
        return ResponseEntity.ok(ChatApiMapper.toPaginatedSessions(result,
                session -> listSessionsUseCase.countMessages(session.id())));
    }

    private void sendChunk(SseEmitter emitter, StringBuilder fullText, AtomicInteger tokenCount, String token) {
        try {
            fullText.append(token);
            tokenCount.incrementAndGet();
            emitter.send(SseEmitter.event()
                    .name("chunk")
                    .data(Map.of("text", token)));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void completeStream(SseEmitter emitter, StreamingChatResponse streaming,
                                StringBuilder fullText, AtomicInteger tokenCount) {
        try {
            int approxTokens = fullText.length() / 4;
            streaming.onComplete().accept(fullText.toString(), approxTokens);

            emitter.send(SseEmitter.event()
                    .name("sources")
                    .data(Map.of("sources", streaming.citations())));
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(Map.of(
                            "messageId", streaming.assistantMessageId().value().toString(),
                            "tokensUsed", approxTokens)));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void handleError(SseEmitter emitter, StreamingChatResponse streaming, Throwable error) {
        try {
            streaming.onError().accept(error);
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of(
                            "message", error.getMessage() == null ? "Unknown error" : error.getMessage(),
                            "code", "LLM_ERROR")));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private static StartSessionCommand toCommand(StartSessionRequest request) {
        if (request == null) {
            return new StartSessionCommand(Optional.empty(), Optional.empty(), Map.of());
        }
        Map<String, Object> metadata = request.getMetadata() == null ? Map.of() : request.getMetadata();
        return new StartSessionCommand(
                Optional.ofNullable(request.getAnonymousId()).filter(s -> !s.isBlank()),
                Optional.ofNullable(request.getExternalId()).filter(s -> !s.isBlank()),
                metadata);
    }
}
