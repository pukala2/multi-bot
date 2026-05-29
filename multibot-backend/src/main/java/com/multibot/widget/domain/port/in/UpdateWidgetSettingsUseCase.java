package com.multibot.widget.domain.port.in;

import com.multibot.widget.domain.model.WidgetSettings;

import java.util.List;

public interface UpdateWidgetSettingsUseCase {

    WidgetSettings update(WidgetSettings settings);

    /**
     * Ustawia dozwolone domeny (allowed_origins) aktywnego bota. Wejscie moze byc URL-em lub samą
     * domeną — normalizowane do origin (scheme://host[:port]). Pusta lista = brak ograniczeń (dev).
     * Zwraca znormalizowaną listę.
     */
    List<String> updateAllowedOriginsForCurrentTenant(List<String> originsOrUrls);
}
