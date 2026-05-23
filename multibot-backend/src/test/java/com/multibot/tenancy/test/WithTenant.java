package com.multibot.tenancy.test;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@ExtendWith(WithTenantExtension.class)
public @interface WithTenant {

    String DEFAULT = "00000000-0000-7000-8000-000000000001";

    String value() default DEFAULT;
}
