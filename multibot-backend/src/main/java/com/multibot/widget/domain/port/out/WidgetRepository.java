package com.multibot.widget.domain.port.out;

import com.multibot.widget.domain.model.WidgetIcon;
import com.multibot.widget.domain.model.WidgetSettings;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WidgetRepository {

    Optional<WidgetSettings> findSettings(UUID tenantId);

    void insertSettings(UUID tenantId, WidgetSettings settings);

    void updateSettings(UUID tenantId, WidgetSettings settings);

    Optional<String> findActiveKey(UUID tenantId);

    void insertKey(UUID tenantId, String key);

    Optional<UUID> findTenantIdByActiveKey(String key);

    void touchKey(String key);

    /** Lista dozwolonych origins dla danego klucza widgeta. Pusta lista = bez ograniczen (dev). */
    List<String> findAllowedOrigins(String key);

    /** Dozwolone origins aktywnego klucza danego tenanta (bota). */
    List<String> findAllowedOriginsByTenant(UUID tenantId);

    /** Ustawia dozwolone origins na aktywnym kluczu danego tenanta (bota). */
    void updateAllowedOriginsByTenant(UUID tenantId, List<String> origins);

    // --- ikonka (obrazek per bot) ---

    /** Zapisuje/zastępuje obrazek ikonki tenanta + ustawia icon_updated_at. */
    void saveIcon(UUID tenantId, byte[] bytes, String mime, Instant updatedAt);

    /** Usuwa obrazek ikonki tenanta (czyści bytes/mime/updated_at). */
    void deleteIcon(UUID tenantId);

    /** Epoch millis icon_updated_at tenanta; empty gdy brak ikonki. */
    Optional<Long> findIconVersionByTenant(UUID tenantId);

    /** Epoch millis icon_updated_at po aktywnym kluczu widgetu; empty gdy brak. */
    Optional<Long> findIconVersionByKey(String key);

    /** Obrazek ikonki po aktywnym kluczu widgetu; empty gdy brak. */
    Optional<WidgetIcon> findIconByKey(String key);
}
