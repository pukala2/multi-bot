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
        return ResponseEntity.ok(new WidgetAdminConfigResponse(config.key(), toResponse(config.settings())));
    }

    @Override
    public ResponseEntity<WidgetSettingsResponse> updateWidgetSettings(WidgetSettingsRequest request) {
        WidgetSettings updated = updateSettings.update(toDomain(request));
        return ResponseEntity.ok(toResponse(updated));
    }

    @Override
    public ResponseEntity<WidgetSettingsResponse> getWidgetPublicConfig(String key) {
        // Origin check: jezeli klucz ma whitelist domen i Origin sie nie zgadza -> 403
        String origin = httpRequest.getHeader("Origin");
        if (!getConfig.isOriginAllowed(key, origin)) {
            throw new ForbiddenException("Origin '" + origin + "' is not allowed for this widget key");
        }
        WidgetSettings settings = getConfig.getByPublicKey(key)
                .orElseThrow(() -> new NotFoundException("Widget key not found: " + key));
        return ResponseEntity.ok(toResponse(settings));
    }

    private static WidgetSettingsResponse toResponse(WidgetSettings s) {
        return new WidgetSettingsResponse(
                s.accentColor(),
                WidgetSettingsResponse.PositionEnum.fromValue(s.position()),
                s.title(),
                s.greeting(),
                s.placeholder(),
                s.isActive());
    }

    private static WidgetSettings toDomain(WidgetSettingsRequest r) {
        return new WidgetSettings(
                r.getAccentColor(),
                r.getPosition().getValue(),
                r.getTitle(),
                r.getGreeting(),
                r.getPlaceholder(),
                r.getIsActive());
    }
}
