package com.multibot.tenancy.test;

import com.multibot.shared.tenant.TenantId;
import com.multibot.tenancy.TenantContext;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.AnnotatedElement;
import java.util.Optional;
import java.util.UUID;

public class WithTenantExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        findAnnotation(context).ifPresent(annotation ->
                TenantContext.set(TenantId.of(UUID.fromString(annotation.value()))));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        TenantContext.clear();
    }

    private Optional<WithTenant> findAnnotation(ExtensionContext context) {
        return context.getTestMethod()
                .map(method -> method.getAnnotation(WithTenant.class))
                .or(() -> context.getTestClass().map(cls -> cls.getAnnotation(WithTenant.class)))
                .or(() -> context.getElement().flatMap(this::fromElement));
    }

    private Optional<WithTenant> fromElement(AnnotatedElement element) {
        return Optional.ofNullable(element.getAnnotation(WithTenant.class));
    }
}
