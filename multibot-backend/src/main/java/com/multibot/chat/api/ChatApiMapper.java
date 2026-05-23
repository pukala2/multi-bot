package com.multibot.chat.api;

import com.multibot.api.generated.model.ChatSessionResponse;
import com.multibot.api.generated.model.ChatSessionSummary;
import com.multibot.api.generated.model.Citation;
import com.multibot.api.generated.model.MessageResponse;
import com.multibot.api.generated.model.MessageRole;
import com.multibot.api.generated.model.PageMetadata;
import com.multibot.api.generated.model.PaginatedChatSessionResponse;
import com.multibot.api.generated.model.PaginatedMessageResponse;
import com.multibot.chat.domain.model.ChatSession;
import com.multibot.chat.domain.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

final class ChatApiMapper {

    private ChatApiMapper() {
    }

    static ChatSessionResponse toResponse(ChatSession session) {
        ChatSessionResponse response = new ChatSessionResponse(
                session.id().value(),
                session.endUser().anonymousId(),
                toOffsetDateTime(session.startedAt()),
                toOffsetDateTime(session.lastActivityAt()));
        session.endUser().externalId().ifPresent(response::setExternalId);
        return response;
    }

    static MessageResponse toResponse(Message message) {
        MessageResponse response = new MessageResponse(
                message.id().value(),
                toApiRole(message.role()),
                message.content(),
                toOffsetDateTime(message.createdAt()));

        if (!message.sources().isEmpty()) {
            List<Citation> apiCitations = message.sources().stream()
                    .map(ChatApiMapper::toApiCitation)
                    .toList();
            response.setSources(apiCitations);
        }
        message.tokensUsed().ifPresent(response::setTokensUsed);
        return response;
    }

    static PaginatedChatSessionResponse toPaginatedSessions(Page<ChatSession> page,
                                                            java.util.function.ToLongFunction<ChatSession> messageCounter) {
        List<ChatSessionSummary> content = page.getContent().stream()
                .map(session -> toSummary(session, messageCounter.applyAsLong(session)))
                .toList();
        return new PaginatedChatSessionResponse(content, toPageMetadata(page));
    }

    static PaginatedMessageResponse toPaginatedMessages(Page<Message> page) {
        List<MessageResponse> content = page.getContent().stream()
                .map(ChatApiMapper::toResponse)
                .toList();
        return new PaginatedMessageResponse(content, toPageMetadata(page));
    }

    static Pageable toPageable(Integer page, Integer size, String sortParam, String defaultField, Sort.Direction defaultDir) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        Sort sort = parseSort(sortParam, defaultField, defaultDir);
        return org.springframework.data.domain.PageRequest.of(pageNumber, pageSize, sort);
    }

    private static Sort parseSort(String sortParam, String defaultField, Sort.Direction defaultDir) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(defaultDir, defaultField);
        }
        String[] parts = sortParam.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private static ChatSessionSummary toSummary(ChatSession session, long messageCount) {
        ChatSessionSummary summary = new ChatSessionSummary(
                session.id().value(),
                session.endUser().anonymousId(),
                toOffsetDateTime(session.startedAt()),
                toOffsetDateTime(session.lastActivityAt()),
                (int) messageCount);
        session.endUser().externalId().ifPresent(summary::setExternalId);
        return summary;
    }

    private static Citation toApiCitation(com.multibot.chat.domain.model.Citation domain) {
        return new Citation(
                domain.chunkId(),
                domain.documentId(),
                domain.snippet(),
                domain.score());
    }

    private static PageMetadata toPageMetadata(Page<?> page) {
        return new PageMetadata(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    private static MessageRole toApiRole(com.multibot.chat.domain.model.MessageRole role) {
        return MessageRole.valueOf(role.name());
    }

    private static OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
