package com.multibot.document.api;

import com.multibot.api.generated.api.DocumentsApi;
import com.multibot.api.generated.model.DocumentResponse;
import com.multibot.api.generated.model.DocumentStatusResponse;
import com.multibot.api.generated.model.PaginatedDocumentResponse;
import com.multibot.document.domain.model.Document;
import com.multibot.document.domain.model.DocumentId;
import com.multibot.document.domain.model.UploadDocumentCommand;
import com.multibot.document.domain.port.in.DeleteDocumentUseCase;
import com.multibot.document.domain.port.in.GetDocumentStatusUseCase;
import com.multibot.document.domain.port.in.GetDocumentUseCase;
import com.multibot.document.domain.port.in.ListDocumentsUseCase;
import com.multibot.document.domain.port.in.UploadDocumentUseCase;
import com.multibot.shared.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

@RestController
class DocumentController implements DocumentsApi {

    private final UploadDocumentUseCase uploadDocumentUseCase;
    private final GetDocumentUseCase getDocumentUseCase;
    private final GetDocumentStatusUseCase getDocumentStatusUseCase;
    private final ListDocumentsUseCase listDocumentsUseCase;
    private final DeleteDocumentUseCase deleteDocumentUseCase;

    DocumentController(UploadDocumentUseCase uploadDocumentUseCase,
                       GetDocumentUseCase getDocumentUseCase,
                       GetDocumentStatusUseCase getDocumentStatusUseCase,
                       ListDocumentsUseCase listDocumentsUseCase,
                       DeleteDocumentUseCase deleteDocumentUseCase) {
        this.uploadDocumentUseCase = uploadDocumentUseCase;
        this.getDocumentUseCase = getDocumentUseCase;
        this.getDocumentStatusUseCase = getDocumentStatusUseCase;
        this.listDocumentsUseCase = listDocumentsUseCase;
        this.deleteDocumentUseCase = deleteDocumentUseCase;
    }

    @Override
    public ResponseEntity<DocumentResponse> uploadDocument(MultipartFile file, UUID versionId, String filename) {
        if (file.isEmpty()) {
            throw new ValidationException("Uploaded file must not be empty");
        }

        String effectiveFilename = (filename != null && !filename.isBlank())
                ? filename
                : file.getOriginalFilename();
        if (effectiveFilename == null || effectiveFilename.isBlank()) {
            throw new ValidationException("Filename is required (either as form field or original file name)");
        }

        String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        DocumentUploadValidator.validate(effectiveFilename, mimeType);

        Document uploaded;
        try {
            uploaded = uploadDocumentUseCase.upload(
                    new UploadDocumentCommand(effectiveFilename, mimeType, file.getBytes(),
                            java.util.Optional.ofNullable(versionId)));
        } catch (IOException e) {
            throw new ValidationException("Failed to read uploaded file: " + e.getMessage(), e);
        }

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(uploaded.id().value())
                .toUri();

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .location(location)
                .body(DocumentApiMapper.toResponse(uploaded));
    }

    @Override
    public ResponseEntity<DocumentResponse> getDocument(UUID id) {
        Document document = getDocumentUseCase.getById(DocumentId.of(id));
        return ResponseEntity.ok(DocumentApiMapper.toResponse(document));
    }

    @Override
    public ResponseEntity<DocumentStatusResponse> getDocumentStatus(UUID id) {
        Document document = getDocumentStatusUseCase.getStatus(DocumentId.of(id));
        return ResponseEntity.ok(DocumentApiMapper.toStatusResponse(document));
    }

    @Override
    public ResponseEntity<PaginatedDocumentResponse> listDocuments(Integer page, Integer size, String sort, UUID versionId) {
        var pageable = DocumentApiMapper.toPageable(page, size, sort);
        var result = versionId != null
                ? listDocumentsUseCase.listByVersion(versionId, pageable)
                : listDocumentsUseCase.list(pageable);
        return ResponseEntity.ok(DocumentApiMapper.toPaginatedResponse(result));
    }

    @Override
    public ResponseEntity<Void> deleteDocument(UUID id) {
        deleteDocumentUseCase.delete(DocumentId.of(id));
        return ResponseEntity.noContent().build();
    }
}
