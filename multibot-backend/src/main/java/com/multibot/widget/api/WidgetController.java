package com.multibot.widget.api;

import com.multibot.api.generated.api.WidgetAdminApi;
import com.multibot.api.generated.api.WidgetApi;
import com.multibot.api.generated.model.WidgetAdminConfigResponse;
import com.multibot.api.generated.model.WidgetSettingsRequest;
import com.multibot.api.generated.model.WidgetSettingsResponse;
import com.multibot.shared.exception.ForbiddenException;
import com.multibot.shared.exception.NotFoundException;
import com.multibot.widget.domain.model.WidgetConfig;
import com.multibot.widget.domain.model.WidgetSettings;
import com.multibot.widget.domain.port.in.GetWidgetConfigUseCase;
import com.multibot.widget.domain.port.in.UpdateWidgetSettingsUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class WidgetController implements WidgetApi, WidgetAdminApi {

    private final GetWidgetConfigUseCase getConfig;
    private final UpdateWidgetSettingsUseCase updateSettings;

    @Autowired
    private HttpServletRequest httpRequest;

    WidgetController(GetWidgetConfigUseCase getConfig, UpdateWidgetSettingsUseCase updateSettings) {
        this.getConfig = getConfig;
        this.updateSettings = updateSettings;
    }

    @Override
    public ResponseEntity<WidgetAdminConfigResponse> getWidgetAdminConfig() {
        WidgetConfig config = getConfig.getForCurrentTenant();
        return ResponseEntity.ok(new WidgetAdminConfigResponse(config.key(), toResponse(config)));
    }

    @Override
    public ResponseEntity<WidgetSettingsResponse> updateWidgetSettings(WidgetSettingsRequest request) {
        updateSettings.update(toDomain(request));
        // Pełny config (z key + iconVersion) żeby zwrócić aktualny iconUrl.
        return ResponseEntity.ok(toResponse(getConfig.getForCurrentTenant()));
    }

    @Override
    public ResponseEntity<WidgetSettingsResponse> getWidgetPublicConfig(String key) {
        // Origin check: jezeli klucz ma whitelist domen i Origin sie nie zgadza -> 403
        String origin = httpRequest.getHeader("Origin");
        if (!getConfig.isOriginAllowed(key, origin)) {
            throw new ForbiddenException("Origin '" + origin + "' is not allowed for this widget key");
        }
        WidgetConfig config = getConfig.getByPublicKey(key)
                .orElseThrow(() -> new NotFoundException("Widget key not found: " + key));
        return ResponseEntity.ok(toResponse(config));
    }

    /** Mapowanie domeny -> response. iconUrl budowany z klucza + wersji (cache-busting), null gdy brak ikonki. */
    static WidgetSettingsResponse toResponse(WidgetConfig config) {
        WidgetSettings s = config.settings();
        WidgetSettingsResponse resp = new WidgetSettingsResponse(
                s.accentColor(),
                WidgetSettingsResponse.PositionEnum.fromValue(s.position()),
                s.title(),
                s.greeting(),
                s.placeholder(),
                s.isActive());
        resp.setIconColor(s.iconColor());
        resp.setIconUrl(config.iconVersion() == null
                ? null
                : "/api/widget/icon/" + config.key() + "?v=" + config.iconVersion());
        return resp;
    }

    private static WidgetSettings toDomain(WidgetSettingsRequest r) {
        String iconColor = (r.getIconColor() == null || r.getIconColor().isBlank()) ? null : r.getIconColor();
        return new WidgetSettings(
                r.getAccentColor(),
                iconColor,
                r.getPosition().getValue(),
                r.getTitle(),
                r.getGreeting(),
                r.getPlaceholder(),
                r.getIsActive());
    }
}
