package com.multibot.botversion.api;

import com.multibot.api.generated.api.BotVersionsApi;
import com.multibot.api.generated.model.ChatbotVersionResponse;
import com.multibot.api.generated.model.CreateVersionRequest;
import com.multibot.botversion.domain.model.ChatbotVersion;
import com.multibot.botversion.domain.port.in.ActivateVersionUseCase;
import com.multibot.botversion.domain.port.in.CreateVersionUseCase;
import com.multibot.botversion.domain.port.in.DeleteVersionUseCase;
import com.multibot.botversion.domain.port.in.GetActiveVersionUseCase;
import com.multibot.botversion.domain.port.in.ListVersionsUseCase;
import com.multibot.botversion.domain.port.in.VersionDocumentsUseCase;
import com.multibot.shared.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
class ChatbotVersionController implements BotVersionsApi {

    private final ListVersionsUseCase listUseCase;
    private final CreateVersionUseCase createUseCase;
    private final ActivateVersionUseCase activateUseCase;
    private final DeleteVersionUseCase deleteUseCase;
    private final GetActiveVersionUseCase activeUseCase;
    private final VersionDocumentsUseCase docsUseCase;

    ChatbotVersionController(ListVersionsUseCase listUseCase,
                             CreateVersionUseCase createUseCase,
                             ActivateVersionUseCase activateUseCase,
                             DeleteVersionUseCase deleteUseCase,
                             GetActiveVersionUseCase activeUseCase,
                             VersionDocumentsUseCase docsUseCase) {
        this.listUseCase = listUseCase;
        this.createUseCase = createUseCase;
        this.activateUseCase = activateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.activeUseCase = activeUseCase;
        this.docsUseCase = docsUseCase;
    }

    @Override
    public ResponseEntity<List<ChatbotVersionResponse>> listVersions() {
        List<ChatbotVersionResponse> response = listUseCase.list().stream()
                .map(ChatbotVersionController::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ChatbotVersionResponse> createVersion(CreateVersionRequest request) {
        Optional<UUID> copyFrom = Optional.ofNullable(request.getCopyFromVersionId());
        ChatbotVersion created = createUseCase.create(request.getName(), copyFrom);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<ChatbotVersionResponse> getActiveVersion() {
        return activeUseCase.getActive()
                .map(v -> ResponseEntity.ok(toResponse(v)))
                .orElseThrow(() -> new NotFoundException("No active version for tenant"));
    }

    @Override
    public ResponseEntity<Void> deleteVersion(UUID id) {
        deleteUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ChatbotVersionResponse> activateVersion(UUID id) {
        return ResponseEntity.ok(toResponse(activateUseCase.activate(id)));
    }

    @Override
    public ResponseEntity<List<UUID>> getVersionDocuments(UUID id) {
        return ResponseEntity.ok(docsUseCase.getDocumentIdsInVersion(id).stream().toList());
    }

    @Override
    public ResponseEntity<Void> addDocumentToVersion(UUID id, UUID documentId) {
        docsUseCase.addDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeDocumentFromVersion(UUID id, UUID documentId) {
        docsUseCase.removeDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }

    private static ChatbotVersionResponse toResponse(ChatbotVersion v) {
        ChatbotVersionResponse r = new ChatbotVersionResponse(
                v.id(),
                v.name(),
                ChatbotVersionResponse.StatusEnum.fromValue(v.status().name()),
                v.createdAt().atOffset(ZoneOffset.UTC),
                v.documentCount());
        v.publishedAt().map(i -> i.atOffset(ZoneOffset.UTC)).ifPresent(r::setPublishedAt);
        return r;
    }
}
