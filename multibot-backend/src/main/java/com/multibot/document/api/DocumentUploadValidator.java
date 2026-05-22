package com.multibot.document.api;

import com.multibot.shared.exception.ValidationException;

import java.util.Set;

final class DocumentUploadValidator {

    static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx", ".txt", ".md");

    static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain",
            "text/markdown",
            "text/x-markdown",
            "application/octet-stream"
    );

    private DocumentUploadValidator() {
    }

    static void validate(String filename, String mimeType) {
        String extension = extractExtension(filename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ValidationException(
                    "Unsupported file extension '%s'. Allowed: %s".formatted(extension, ALLOWED_EXTENSIONS));
        }
        if (mimeType != null && !ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new ValidationException(
                    "Unsupported content type '%s'. Allowed: %s".formatted(mimeType, ALLOWED_MIME_TYPES));
        }
    }

    private static String extractExtension(String filename) {
        int idx = filename.lastIndexOf('.');
        return (idx > 0 && idx < filename.length() - 1) ? filename.substring(idx) : "";
    }
}
