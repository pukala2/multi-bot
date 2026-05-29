package com.multibot.organization.application;

import com.multibot.organization.domain.exception.OrganizationNotFoundException;
import com.multibot.organization.domain.model.Organization;
import com.multibot.organization.domain.model.OrganizationSlug;
import com.multibot.organization.domain.model.OrganizationStatus;
import com.multibot.organization.domain.port.in.CreateOrganizationUseCase;
import com.multibot.organization.domain.port.in.GetOrganizationUseCase;
import com.multibot.organization.domain.port.in.ListOrganizationsUseCase;
import com.multibot.organization.domain.port.out.OrganizationRepository;
import com.multibot.shared.organization.OrganizationId;
import com.multibot.shared.util.UuidGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
class OrganizationService implements GetOrganizationUseCase, CreateOrganizationUseCase, ListOrganizationsUseCase {

    private final OrganizationRepository repository;

    OrganizationService(OrganizationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Organization getById(OrganizationId id) {
        return repository.findById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));
    }

    @Override
    public List<Organization> listAll() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public Organization create(String name, String planCode, String companyName, String nip) {
        OrganizationSlug slug = uniqueSlug(name);
        Instant now = Instant.now();
        Organization org = new Organization(
                OrganizationId.of(UuidGenerator.newUuidV7()),
                name,
                slug,
                (planCode == null || planCode.isBlank()) ? "FREE" : planCode.trim().toUpperCase(Locale.ROOT),
                OrganizationStatus.ACTIVE,
                (companyName == null || companyName.isBlank()) ? null : companyName,
                (nip == null || nip.isBlank()) ? null : nip,
                null,
                now, now);
        return repository.save(org);
    }

    private OrganizationSlug uniqueSlug(String name) {
        String base = slugify(name);
        OrganizationSlug candidate = OrganizationSlug.of(base);
        int i = 2;
        while (repository.existsBySlug(candidate)) {
            candidate = OrganizationSlug.of(base + "-" + i++);
        }
        return candidate;
    }

    private static String slugify(String name) {
        String s = Normalizer.normalize(name.replace("ł", "l").replace("Ł", "L"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (s.length() < 3) s = "org-" + (s.isBlank() ? "x" : s);
        if (s.length() > 64) s = s.substring(0, 64).replaceAll("-+$", "");
        return s;
    }
}
