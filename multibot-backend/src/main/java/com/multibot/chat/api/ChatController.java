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
import com.multibot.chat.domain.port.in.GetSessionHistoryUseCase;
import com.multibot.chat.domain.port.in.ListSessionsUseCase;
import com.multibot.chat.domain.port.in.SendMessageUseCase;
import com.multibot.chat.domain.port.in.StartSessionUseCase;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
class ChatController implements ChatApi {

    private final StartSessionUseCase startSessionUseCase;
    private final SendMessageUseCase sendMessageUseCase;
    private final GetSessionHistoryUseCase getSessionHistoryUseCase;
    private final ListSessionsUseCase listSessionsUseCase;

    ChatController(StartSessionUseCase startSessionUseCase,
                   SendMessageUseCase sendMessageUseCase,
                   GetSessionHistoryUseCase getSessionHistoryUseCase,
                   ListSessionsUseCase listSessionsUseCase) {
        this.startSessionUseCase = startSessionUseCase;
        this.sendMessageUseCase = sendMessageUseCase;
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
