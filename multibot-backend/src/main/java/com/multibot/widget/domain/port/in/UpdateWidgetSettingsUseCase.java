package com.multibot.widget.domain.port.in;

import com.multibot.widget.domain.model.WidgetConfig;
import com.multibot.widget.domain.model.WidgetSettings;

import java.util.List;

public interface UpdateWidgetSettingsUseCase {

    WidgetSettings update(WidgetSettings settings);

    /**
     * Wgrywa własną ikonkę aktywnego bota. bytes — surowy obrazek; contentType z uploadu (do wstępnej oceny).
     * Walidacja: rozmiar ≤ 512 KB, format po MAGIC BYTES (PNG/JPEG/WebP), SVG/inne odrzucone (ValidationException).
     * Zwraca świeży WidgetConfig (z iconVersion) do odświeżenia panelu.
     */
    WidgetConfig uploadIconForCurrentTenant(byte[] bytes, String contentType);

    /** Usuwa własną ikonkę aktywnego bota (powrót do domyślnej). */
    WidgetConfig deleteIconForCurrentTenant();

    /**
     * Ustawia dozwolone domeny (allowed_origins) aktywnego bota. Wejscie moze byc URL-em lub samą
     * domeną — normalizowane do origin (scheme://host[:port]). Pusta lista = brak ograniczeń (dev).
     * Zwraca znormalizowaną listę.
     */
    List<String> updateAllowedOriginsForCurrentTenant(List<String> originsOrUrls);
}
