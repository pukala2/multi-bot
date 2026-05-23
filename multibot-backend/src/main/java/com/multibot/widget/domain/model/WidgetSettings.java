package com.multibot.widget.domain.model;

public record WidgetSettings(
        String accentColor,
        String position,
        String title,
        String greeting,
        String placeholder,
        boolean isActive) {

    public static WidgetSettings defaults() {
        return new WidgetSettings("#7B5BFF", "bottom-right", "Asystent",
                "Cześć! Jak mogę pomóc?", "Napisz wiadomość…", true);
    }
}
