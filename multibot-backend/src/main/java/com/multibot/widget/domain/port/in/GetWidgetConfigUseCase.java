package com.multibot.widget.domain.port.in;

import com.multibot.widget.domain.model.WidgetConfig;
import com.multibot.widget.domain.model.WidgetSettings;

import java.util.Optional;

public interface GetWidgetConfigUseCase {

    WidgetConfig getForCurrentTenant();

    Optional<WidgetSettings> getByPublicKey(String key);
}
