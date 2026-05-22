CREATE TABLE tenants
(
    id         UUID                     PRIMARY KEY,
    name       VARCHAR(255)             NOT NULL,
    slug       VARCHAR(64)              NOT NULL,
    plan       VARCHAR(32)              NOT NULL DEFAULT 'FREE',
    status     VARCHAR(32)              NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by UUID,
    updated_by UUID,

    CONSTRAINT uq_tenants_slug UNIQUE (slug),
    CONSTRAINT ck_tenants_plan CHECK (plan IN ('FREE', 'BASIC', 'PRO')),
    CONSTRAINT ck_tenants_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_tenants_status ON tenants (status);
