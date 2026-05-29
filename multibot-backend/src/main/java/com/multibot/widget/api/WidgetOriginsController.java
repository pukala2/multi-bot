package com.multibot.widget.api;

import com.multibot.api.generated.api.WidgetOriginsApi;
import com.multibot.api.generated.model.WidgetOriginsRequest;
import com.multibot.api.generated.model.WidgetOriginsResponse;
import com.multibot.widget.domain.port.in.GetWidgetConfigUseCase;
import com.multibot.widget.domain.port.in.UpdateWidgetSettingsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Zarzadzanie dozwolonymi domenami widgetu aktywnego bota (allowed_origins).
 * Contract-first (implementuje {@link WidgetOriginsApi}). Tenant = aktywny bot (z sesji).
 */
@RestController
class WidgetOriginsController implements WidgetOriginsApi {

    private final GetWidgetConfigUseCase getConfig;
    private final UpdateWidgetSettingsUseCase updateSettings;

    WidgetOriginsController(GetWidgetConfigUseCase getConfig, UpdateWidgetSettingsUseCase updateSettings) {
        this.getConfig = getConfig;
        this.updateSettings = updateSettings;
    }

    @Override
    public ResponseEntity<WidgetOriginsResponse> getWidgetOrigins() {
        return ResponseEntity.ok(new WidgetOriginsResponse(getConfig.getAllowedOriginsForCurrentTenant()));
    }

    @Override
    public ResponseEntity<WidgetOriginsResponse> updateWidgetOrigins(WidgetOriginsRequest widgetOriginsRequest) {
        List<String> saved = updateSettings.updateAllowedOriginsForCurrentTenant(
                widgetOriginsRequest.getOrigins() == null ? List.of() : widgetOriginsRequest.getOrigins());
        return ResponseEntity.ok(new WidgetOriginsResponse(saved));
    }
}
