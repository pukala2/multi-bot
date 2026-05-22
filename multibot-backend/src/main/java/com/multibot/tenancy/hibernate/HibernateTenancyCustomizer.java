package com.multibot.tenancy.hibernate;

import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class HibernateTenancyCustomizer implements HibernatePropertiesCustomizer {

    private final TenantIdResolver tenantIdResolver;

    public HibernateTenancyCustomizer(TenantIdResolver tenantIdResolver) {
        this.tenantIdResolver = tenantIdResolver;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdResolver);
    }
}
