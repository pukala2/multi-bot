package com.multibot.widget.domain.port.out;

import com.multibot.widget.domain.model.WidgetSettings;

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
}
