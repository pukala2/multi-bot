package com.multibot.widget.application;

import com.multibot.shared.exception.ValidationException;
import com.multibot.tenancy.TenantContext;
import com.multibot.widget.domain.model.WidgetConfig;
import com.multibot.widget.domain.model.WidgetSettings;
import com.multibot.widget.domain.port.in.GetWidgetConfigUseCase;
import com.multibot.widget.domain.port.in.UpdateWidgetSettingsUseCase;
import com.multibot.widget.domain.port.out.WidgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
class WidgetService implements GetWidgetConfigUseCase, UpdateWidgetSettingsUseCase {

    private static final Set<String> ALLOWED_POSITIONS = Set.of("bottom-right", "bottom-left");
    private static final SecureRandom RND = new SecureRandom();

    private final WidgetRepository repository;

    WidgetService(WidgetRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public WidgetConfig getForCurrentTenant() {
        UUID tenantId = TenantContext.get().value();
        WidgetSettings settings = findOrCreateSettings(tenantId);
        String key = findOrCreateKey(tenantId);
        return new WidgetConfig(key, settings);
    }

    @Override
    @Transactional
    public Optional<WidgetSettings> getByPublicKey(String key) {
        return repository.findTenantIdByActiveKey(key)
                .map(tenantId -> {
                    repository.touchKey(key);
                    return repository.findSettings(tenantId).orElseGet(WidgetSettings::defaults);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOriginAllowed(String key, String origin) {
        List<String> allowed = repository.findAllowedOrigins(key);
        if (allowed.isEmpty()) return true;     // pusta lista = bez ograniczen (dev)
        if (origin == null || origin.isBlank()) return false;
        return allowed.contains(origin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllowedOrigins(String key) {
        return repository.findAllowedOrigins(key);
    }

    @Override
    @Transactional
    public WidgetSettings update(WidgetSettings settings) {
        validate(settings);
        UUID tenantId = TenantContext.get().value();
        findOrCreateSettings(tenantId);
        repository.updateSettings(tenantId, settings);
        return repository.findSettings(tenantId).orElse(settings);
    }

    private WidgetSettings findOrCreateSettings(UUID tenantId) {
        return repository.findSettings(tenantId).orElseGet(() -> {
            WidgetSettings defaults = WidgetSettings.defaults();
            repository.insertSettings(tenantId, defaults);
            return defaults;
        });
    }

    private String findOrCreateKey(UUID tenantId) {
        return repository.findActiveKey(tenantId).orElseGet(() -> {
            String newKey = generateKey();
            repository.insertKey(tenantId, newKey);
            return newKey;
        });
    }

    private void validate(WidgetSettings s) {
        if (s.accentColor() == null || !s.accentColor().matches("^#[0-9A-Fa-f]{6}$")) {
            throw new ValidationException("accentColor must be a hex color like #RRGGBB");
        }
        if (!ALLOWED_POSITIONS.contains(s.position())) {
            throw new ValidationException("position must be one of: bottom-right, bottom-left");
        }
        if (s.title() == null || s.title().isBlank() || s.title().length() > 60) {
            throw new ValidationException("title is required, max 60 chars");
        }
        if (s.greeting() == null || s.greeting().isBlank()) {
            throw new ValidationException("greeting is required");
        }
        if (s.placeholder() == null || s.placeholder().isBlank() || s.placeholder().length() > 80) {
            throw new ValidationException("placeholder is required, max 80 chars");
        }
    }

    private static String generateKey() {
        byte[] bytes = new byte[8];
        RND.nextBytes(bytes);
        return "pk_widget_live_" + HexFormat.of().formatHex(bytes);
    }
}
