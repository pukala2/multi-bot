package com.multibot.widget.api;

import com.multibot.widget.domain.port.in.GetWidgetConfigUseCase;
import com.multibot.widget.domain.port.in.UpdateWidgetSettingsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Zarzadzanie dozwolonymi domenami widgetu aktywnego bota (allowed_origins).
 * Ręczny kontroler (poza openapi.yaml). Tenant = aktywny bot (z sesji).
 */
@RestController
@RequestMapping("/api/admin/widget/origins")
class WidgetOriginsController {

    private final GetWidgetConfigUseCase getConfig;
    private final UpdateWidgetSettingsUseCase updateSettings;

    WidgetOriginsController(GetWidgetConfigUseCase getConfig, UpdateWidgetSettingsUseCase updateSettings) {
        this.getConfig = getConfig;
        this.updateSettings = updateSettings;
    }

    @GetMapping
    ResponseEntity<Map<String, Object>> get() {
        return ResponseEntity.ok(Map.of("origins", getConfig.getAllowedOriginsForCurrentTenant()));
    }

    @PutMapping
    ResponseEntity<Map<String, Object>> update(@RequestBody OriginsRequest req) {
        List<String> saved = updateSettings.updateAllowedOriginsForCurrentTenant(
                req.origins() == null ? List.of() : req.origins());
        return ResponseEntity.ok(Map.of("origins", saved));
    }

    record OriginsRequest(List<String> origins) {
    }
}
