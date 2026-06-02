package com.multibot.widget.api;

import com.multibot.api.generated.api.WidgetIconApi;
import com.multibot.api.generated.model.WidgetSettingsResponse;
import com.multibot.shared.exception.NotFoundException;
import com.multibot.shared.exception.ValidationException;
import com.multibot.widget.domain.model.WidgetConfig;
import com.multibot.widget.domain.model.WidgetIcon;
import com.multibot.widget.domain.port.in.GetWidgetConfigUseCase;
import com.multibot.widget.domain.port.in.UpdateWidgetSettingsUseCase;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/**
 * Własna ikonka bota: upload/usuń (admin, aktywny bot z TenantContext) + publiczne serwowanie po kluczu.
 * Contract-first (implementuje {@link WidgetIconApi}).
 */
@RestController
class WidgetIconController implements WidgetIconApi {

    private final UpdateWidgetSettingsUseCase updateSettings;
    private final GetWidgetConfigUseCase getConfig;

    WidgetIconController(UpdateWidgetSettingsUseCase updateSettings, GetWidgetConfigUseCase getConfig) {
        this.updateSettings = updateSettings;
        this.getConfig = getConfig;
    }

    @Override
    public ResponseEntity<WidgetSettingsResponse> uploadWidgetIcon(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Nie udało się odczytać pliku");
        }
        WidgetConfig config = updateSettings.uploadIconForCurrentTenant(bytes, file.getContentType());
        return ResponseEntity.ok(WidgetController.toResponse(config));
    }

    @Override
    public ResponseEntity<WidgetSettingsResponse> deleteWidgetIcon() {
        WidgetConfig config = updateSettings.deleteIconForCurrentTenant();
        return ResponseEntity.ok(WidgetController.toResponse(config));
    }

    @Override
    public ResponseEntity<Resource> getWidgetIcon(String key) {
        WidgetIcon icon = getConfig.getIconByKey(key)
                .orElseThrow(() -> new NotFoundException("Brak ikonki dla klucza: " + key));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(icon.mimeType()))
                // wersjonowany URL (?v=) => można cache'ować długo i immutable
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new ByteArrayResource(icon.bytes()));
    }
}
