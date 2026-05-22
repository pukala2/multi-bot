package com.multibot.tenancy.async;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.springframework.core.task.TaskDecorator;

import java.util.Optional;

public final class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Optional<TenantId> captured = TenantContext.tryGet();
        return () -> {
            captured.ifPresent(TenantContext::set);
            try {
                runnable.run();
            } finally {
                TenantContext.clear();
            }
        };
    }
}
