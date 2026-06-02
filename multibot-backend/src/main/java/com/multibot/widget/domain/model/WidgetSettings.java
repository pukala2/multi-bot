package com.multibot.widget.domain.model;

public record WidgetSettings(
        String accentColor,
        String iconColor,      // nullable — NULL = użyj accentColor dla ikonki/launchera
        String position,
        String title,
        String greeting,
        String placeholder,
        boolean isActive) {

    public static WidgetSettings defaults() {
        return new WidgetSettings("#7B5BFF", null, "bottom-right", "Asystent",
                "Cześć! Jak mogę pomóc?", "Napisz wiadomość…", true);
    }
}
