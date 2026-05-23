package com.multibot.tenancy.async;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;
import java.util.Optional;

public final class TenantAwareTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Optional<TenantId> capturedTenant = TenantContext.tryGet();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        return () -> {
            capturedTenant.ifPresent(TenantContext::set);
            if (capturedMdc != null) {
                MDC.setContextMap(capturedMdc);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
                TenantContext.clear();
            }
        };
    }
}
