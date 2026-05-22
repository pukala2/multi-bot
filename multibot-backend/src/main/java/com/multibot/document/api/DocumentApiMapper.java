package com.multibot.document.api;

import com.multibot.api.generated.model.DocumentResponse;
import com.multibot.api.generated.model.DocumentStatus;
import com.multibot.api.generated.model.DocumentStatusResponse;
import com.multibot.api.generated.model.DocumentSummary;
import com.multibot.api.generated.model.PageMetadata;
import com.multibot.api.generated.model.PaginatedDocumentResponse;
import com.multibot.document.domain.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

final class DocumentApiMapper {

    private DocumentApiMapper() {
    }

    static DocumentResponse toResponse(Document document) {
        DocumentResponse response = new DocumentResponse(
                document.id().value(),
                document.filename(),
                document.mimeType(),
                document.sizeBytes(),
                toApiStatus(document.status()),
                toOffsetDateTime(document.uploadedAt()));
        document.indexedAt().ifPresent(instant -> response.setIndexedAt(toOffsetDateTime(instant)));
        document.errorMessage().ifPresent(response::setErrorMessage);
        return response;
    }

    static DocumentStatusResponse toStatusResponse(Document document) {
        DocumentStatusResponse response = new DocumentStatusResponse(
                document.id().value(),
                toApiStatus(document.status()));
        document.indexedAt().ifPresent(instant -> response.setIndexedAt(toOffsetDateTime(instant)));
        document.errorMessage().ifPresent(response::setErrorMessage);
        return response;
    }

    static PaginatedDocumentResponse toPaginatedResponse(Page<com.multibot.document.domain.model.DocumentSummary> page) {
        List<DocumentSummary> content = page.getContent().stream()
                .map(DocumentApiMapper::toSummary)
                .toList();

        PageMetadata pagination = new PageMetadata(
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());

        return new PaginatedDocumentResponse(content, pagination);
    }

    static Pageable toPageable(Integer page, Integer size, String sortParam) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        Sort sort = parseSort(sortParam);
        return org.springframework.data.domain.PageRequest.of(pageNumber, pageSize, sort);
    }

    private static Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "uploadedAt");
        }
        String[] parts = sortParam.split(",");
        String field = parts[0].trim();
        Sort.Direction direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    private static DocumentSummary toSummary(com.multibot.document.domain.model.DocumentSummary domain) {
        return new DocumentSummary(
                domain.id().value(),
                domain.filename(),
                domain.mimeType(),
                domain.sizeBytes(),
                toApiStatus(domain.status()),
                toOffsetDateTime(domain.uploadedAt()));
    }

    private static DocumentStatus toApiStatus(com.multibot.document.domain.model.DocumentStatus status) {
        return DocumentStatus.valueOf(status.name());
    }

    private static OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
