package com.multibot.widget.domain.model;

/**
 * Konfiguracja widgetu zwracana do panelu/publicznie.
 * iconVersion = epoch millis ostatniej zmiany ikonki (do cache-bustingu URL-a); null = brak własnej ikonki.
 */
public record WidgetConfig(String key, WidgetSettings settings, Long iconVersion) {}
