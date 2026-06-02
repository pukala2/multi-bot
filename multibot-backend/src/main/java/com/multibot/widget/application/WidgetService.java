package com.multibot.widget.application;

import com.multibot.shared.exception.ValidationException;
import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import com.multibot.widget.domain.model.WidgetConfig;
import com.multibot.widget.domain.model.WidgetIcon;
import com.multibot.widget.domain.model.WidgetSettings;
import com.multibot.widget.domain.port.in.GetWidgetConfigUseCase;
import com.multibot.widget.domain.port.in.UpdateWidgetSettingsUseCase;
import com.multibot.widget.domain.port.out.WidgetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
class WidgetService implements GetWidgetConfigUseCase, UpdateWidgetSettingsUseCase {

    private static final Set<String> ALLOWED_POSITIONS = Set.of("bottom-right", "bottom-left");
    private static final SecureRandom RND = new SecureRandom();
    private static final int MAX_ICON_BYTES = 512 * 1024;

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
        Long iconVersion = repository.findIconVersionByTenant(tenantId).orElse(null);
        return new WidgetConfig(key, settings, iconVersion);
    }

    @Override
    @Transactional
    public Optional<WidgetConfig> getByPublicKey(String key) {
        return repository.findTenantIdByActiveKey(key)
                .map(tenantId -> {
                    repository.touchKey(key);
                    WidgetSettings settings = repository.findSettings(tenantId).orElseGet(WidgetSettings::defaults);
                    Long iconVersion = repository.findIconVersionByKey(key).orElse(null);
                    return new WidgetConfig(key, settings, iconVersion);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WidgetIcon> getIconByKey(String key) {
        return repository.findIconByKey(key);
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
    @Transactional(readOnly = true)
    public Optional<TenantId> validateAndResolveTenant(String key, String origin) {
        if (key == null || key.isBlank()) return Optional.empty();
        Optional<UUID> tenantId = repository.findTenantIdByActiveKey(key);
        if (tenantId.isEmpty()) return Optional.empty();
        if (!isOriginAllowed(key, origin)) return Optional.empty();
        return tenantId.map(TenantId::of);
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

    @Override
    @Transactional
    public WidgetConfig uploadIconForCurrentTenant(byte[] bytes, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new ValidationException("Plik jest pusty");
        }
        if (bytes.length > MAX_ICON_BYTES) {
            throw new ValidationException("Obrazek przekracza limit 512 KB");
        }
        String mime = detectImageMime(bytes);   // po MAGIC BYTES, nie po contentType z uploadu
        if (mime == null) {
            throw new ValidationException("Niedozwolony format. Dozwolone: PNG, JPG, WebP (SVG zabronione).");
        }
        UUID tenantId = TenantContext.get().value();
        findOrCreateSettings(tenantId);
        findOrCreateKey(tenantId);
        repository.saveIcon(tenantId, bytes, mime, Instant.now());
        return getForCurrentTenant();
    }

    @Override
    @Transactional
    public WidgetConfig deleteIconForCurrentTenant() {
        UUID tenantId = TenantContext.get().value();
        repository.deleteIcon(tenantId);
        return getForCurrentTenant();
    }

    /** Rozpoznaje format po sygnaturze bajtów. Zwraca MIME albo null gdy nieobsługiwany. */
    private static String detectImageMime(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAllowedOriginsForCurrentTenant() {
        return repository.findAllowedOriginsByTenant(TenantContext.get().value());
    }

    @Override
    @Transactional
    public List<String> updateAllowedOriginsForCurrentTenant(List<String> originsOrUrls) {
        UUID tenantId = TenantContext.get().value();
        findOrCreateKey(tenantId);   // upewnij sie ze klucz istnieje (cel update'u)
        List<String> normalized = new ArrayList<>(new LinkedHashSet<>(
                (originsOrUrls == null ? List.<String>of() : originsOrUrls).stream()
                        .map(WidgetService::normalizeOrigin)
                        .filter(o -> o != null)
                        .toList()));
        repository.updateAllowedOriginsByTenant(tenantId, normalized);
        return normalized;
    }

    /** "silesiaszymanow.pl", "https://silesiaszymanow.pl/sklep" -> "https://silesiaszymanow.pl". */
    private static String normalizeOrigin(String input) {
        if (input == null || input.isBlank()) return null;
        String s = input.trim();
        if (!s.contains("://")) s = "https://" + s;
        try {
            URI uri = URI.create(s);
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase();
            String host = uri.getHost();
            if (host == null || host.isBlank() || (!"http".equals(scheme) && !"https".equals(scheme))) {
                throw new ValidationException("Niepoprawny adres domeny: " + input);
            }
            return scheme + "://" + host.toLowerCase() + (uri.getPort() != -1 ? ":" + uri.getPort() : "");
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Niepoprawny adres domeny: " + input);
        }
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
        if (s.iconColor() != null && !s.iconColor().matches("^#[0-9A-Fa-f]{6}$")) {
            throw new ValidationException("iconColor must be a hex color like #RRGGBB or null");
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
