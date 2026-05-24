package com.multibot.botversion.application;

import com.multibot.botversion.domain.model.ChatbotVersion;
import com.multibot.botversion.domain.port.in.ActivateVersionUseCase;
import com.multibot.botversion.domain.port.in.CreateVersionUseCase;
import com.multibot.botversion.domain.port.in.DeleteVersionUseCase;
import com.multibot.botversion.domain.port.in.GetActiveVersionUseCase;
import com.multibot.botversion.domain.port.in.ListVersionsUseCase;
import com.multibot.botversion.domain.port.in.VersionDocumentsUseCase;
import com.multibot.botversion.domain.port.out.ChatbotVersionRepository;
import com.multibot.shared.exception.ConflictException;
import com.multibot.shared.exception.NotFoundException;
import com.multibot.shared.exception.ValidationException;
import com.multibot.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
class ChatbotVersionService implements
        ListVersionsUseCase, CreateVersionUseCase, ActivateVersionUseCase,
        DeleteVersionUseCase, GetActiveVersionUseCase, VersionDocumentsUseCase {

    private final ChatbotVersionRepository repository;

    ChatbotVersionService(ChatbotVersionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatbotVersion> list() {
        return repository.findAllByTenant(TenantContext.get().value());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChatbotVersion> getActive() {
        return repository.findActive(TenantContext.get().value());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> getActiveVersionId() {
        return repository.findActiveVersionId(TenantContext.get().value());
    }

    @Override
    @Transactional
    public ChatbotVersion create(String name, Optional<UUID> copyFromVersionId) {
        UUID tenantId = TenantContext.get().value();
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new ValidationException("Name is required, max 120 chars");
        }

        UUID newId = UUID.randomUUID();
        repository.insertDraft(newId, tenantId, name);

        copyFromVersionId.ifPresent(sourceId -> {
            repository.findById(sourceId, tenantId)
                    .orElseThrow(() -> new NotFoundException("Source version not found: " + sourceId));
            repository.copyDocuments(sourceId, newId);
        });

        return repository.findById(newId, tenantId)
                .orElseThrow(() -> new IllegalStateException("Just created version not found"));
    }

    @Override
    @Transactional
    public ChatbotVersion activate(UUID versionId) {
        UUID tenantId = TenantContext.get().value();
        ChatbotVersion version = repository.findById(versionId, tenantId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));

        if (version.status() == ChatbotVersion.VersionStatus.ACTIVE) {
            return version;
        }

        repository.activate(versionId, tenantId);
        return repository.findById(versionId, tenantId).orElseThrow();
    }

    @Override
    @Transactional
    public void delete(UUID versionId) {
        UUID tenantId = TenantContext.get().value();
        ChatbotVersion version = repository.findById(versionId, tenantId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));

        if (version.status() == ChatbotVersion.VersionStatus.ACTIVE) {
            throw new ConflictException("Cannot delete active version. Activate another version first.");
        }
        repository.deleteVersion(versionId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> getDocumentIdsInVersion(UUID versionId) {
        UUID tenantId = TenantContext.get().value();
        repository.findById(versionId, tenantId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));
        return repository.getDocumentIdsInVersion(versionId);
    }

    @Override
    @Transactional
    public void addDocument(UUID versionId, UUID documentId) {
        UUID tenantId = TenantContext.get().value();
        ChatbotVersion version = repository.findById(versionId, tenantId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));
        if (version.status() == ChatbotVersion.VersionStatus.ARCHIVED) {
            throw new ConflictException("Cannot modify archived version");
        }
        repository.addDocument(versionId, documentId);
    }

    @Override
    @Transactional
    public void removeDocument(UUID versionId, UUID documentId) {
        UUID tenantId = TenantContext.get().value();
        ChatbotVersion version = repository.findById(versionId, tenantId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));
        if (version.status() == ChatbotVersion.VersionStatus.ARCHIVED) {
            throw new ConflictException("Cannot modify archived version");
        }
        repository.removeDocument(versionId, documentId);
    }
}
