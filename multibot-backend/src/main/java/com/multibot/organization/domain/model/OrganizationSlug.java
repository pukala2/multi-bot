package com.multibot.organization.domain.model;

import com.multibot.shared.exception.ValidationException;

import java.util.Objects;
import java.util.regex.Pattern;

public record OrganizationSlug(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final int MIN_LENGTH = 3;
    private static final int MAX_LENGTH = 64;

    public OrganizationSlug {
        Objects.requireNonNull(value, "Slug must not be null");
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) {
            throw new ValidationException(
                    "Slug must be between %d and %d characters, got %d".formatted(MIN_LENGTH, MAX_LENGTH, value.length()));
        }
        if (!PATTERN.matcher(value).matches()) {
            throw new ValidationException(
                    "Slug must be lowercase alphanumeric with hyphens (e.g. 'acme-corp'), got: " + value);
        }
    }

    public static OrganizationSlug of(String value) {
        return new OrganizationSlug(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
