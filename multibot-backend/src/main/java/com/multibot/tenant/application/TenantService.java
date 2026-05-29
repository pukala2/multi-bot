package com.multibot.tenant.application;

import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.tenant.TenantId;
import com.multibot.shared.util.UuidGenerator;
import com.multibot.tenant.domain.exception.TenantNotFoundException;
import com.multibot.tenant.domain.model.Tenant;
import com.multibot.tenant.domain.model.TenantSlug;
import com.multibot.tenant.domain.model.TenantStatus;
import com.multibot.tenant.domain.port.in.CreateTenantUseCase;
import com.multibot.tenant.domain.port.in.GetTenantUseCase;
import com.multibot.tenant.domain.port.in.ListTenantsUseCase;
import com.multibot.tenant.domain.port.out.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
class TenantService implements GetTenantUseCase, CreateTenantUseCase, ListTenantsUseCase {

    private final TenantRepository tenantRepository;

    TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Tenant getById(TenantId id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(id));
    }

    @Override
    public Tenant getBySlug(TenantSlug slug) {
        return tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new TenantNotFoundException(slug));
    }

    @Override
    public List<Tenant> listAll() {
        return tenantRepository.findAll();
    }

    @Override
    public List<Tenant> listByOrganization(OrganizationId organizationId) {
        return tenantRepository.findByOrganizationId(organizationId);
    }

    @Override
    @Transactional
    public Tenant create(OrganizationId organizationId, String name) {
        TenantSlug slug = uniqueSlug(name);
        Instant now = Instant.now();
        Tenant tenant = new Tenant(
                TenantId.of(UuidGenerator.newUuidV7()),
                name,
                slug,
                organizationId,
                TenantStatus.ACTIVE,
                now, now);
        return tenantRepository.save(tenant);
    }

    /** Slug z nazwy + sufiks liczbowy jesli zajety. */
    private TenantSlug uniqueSlug(String name) {
        String base = slugify(name);
        TenantSlug candidate = TenantSlug.of(base);
        int i = 2;
        while (tenantRepository.existsBySlug(candidate)) {
            candidate = TenantSlug.of(base + "-" + i++);
        }
        return candidate;
    }

    private static String slugify(String name) {
        String s = Normalizer.normalize(name.replace("ł", "l").replace("Ł", "L"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (s.length() < 3) s = "bot-" + (s.isBlank() ? "x" : s);
        if (s.length() > 64) s = s.substring(0, 64).replaceAll("-+$", "");
        return s;
    }
}
