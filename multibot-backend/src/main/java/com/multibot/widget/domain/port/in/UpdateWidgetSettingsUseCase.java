package com.multibot.widget.domain.port.in;

import com.multibot.widget.domain.model.WidgetSettings;

public interface UpdateWidgetSettingsUseCase {

    WidgetSettings update(WidgetSettings settings);
}
