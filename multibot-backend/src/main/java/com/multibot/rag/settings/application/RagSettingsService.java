package com.multibot.rag.settings.application;

import com.multibot.rag.settings.domain.model.RagSettings;
import com.multibot.rag.settings.domain.port.in.GetRagSettingsUseCase;
import com.multibot.rag.settings.domain.port.in.UpdateRagSettingsUseCase;
import com.multibot.rag.settings.domain.port.out.RagSettingsRepository;
import com.multibot.shared.exception.ValidationException;
import com.multibot.tenancy.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class RagSettingsService implements GetRagSettingsUseCase, UpdateRagSettingsUseCase {

    private final RagSettingsRepository repository;

    RagSettingsService(RagSettingsRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public RagSettings getForCurrentTenant() {
        UUID tenantId = TenantContext.get().value();
        return repository.find(tenantId).orElseGet(() -> {
            RagSettings defaults = RagSettings.defaults();
            repository.insert(tenantId, defaults);
            return defaults;
        });
    }

    @Override
    @Transactional
    public RagSettings update(RagSettings settings) {
        validate(settings);
        UUID tenantId = TenantContext.get().value();
        if (repository.find(tenantId).isEmpty()) {
            repository.insert(tenantId, settings);
        } else {
            repository.update(tenantId, settings);
        }
        return repository.find(tenantId).orElse(settings);
    }

    private void validate(RagSettings s) {
        if (s.minScore() < 0.0 || s.minScore() > 1.0) {
            throw new ValidationException("minScore must be in [0, 1], got: " + s.minScore());
        }
        if (s.topK() < 1 || s.topK() > 20) {
            throw new ValidationException("topK must be in [1, 20], got: " + s.topK());
        }
        if (s.temperature() < 0.0 || s.temperature() > 1.0) {
            throw new ValidationException("temperature must be in [0, 1], got: " + s.temperature());
        }
        if (s.systemPrompt().isPresent() && s.systemPrompt().get().length() > 4000) {
            throw new ValidationException("systemPrompt too long (max 4000 chars)");
        }
    }
}
