# Schemat bazy danych

Tabele, relacje, indeksy, klucze obce. Migracje Flyway organizowane per moduł.

> **Aktualizacja (Fazy A–D + pivot Organizacja→Boty):** schemat urósł o auth, billing, security, super admina,
> organizacje i widget. **Migracje Flyway są źródłem prawdy** (`src/main/resources/db/migration/<moduł>/`).
> Kluczowa zmiana: `organizations` (klient) stoi ponad `tenants` (= bot); plan/limity/dane firmowe są na
> organizacji; `users.organization_id` zamiast `tenant_id`. Pełny model: [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md).

Szczegóły pgvector (vector store, indeksy wektorowe, similarity search) → [`pgvector.md`](pgvector.md).

---

## 1. Strategia ogólna

- **Jedna baza PostgreSQL 16** - dane relacyjne + wektory (pgvector) razem
- **Jeden schemat** (`public`) - wszystkie tabele biznesowe + tabele Spring Modulith + tabele Flyway
- **Multi-tenancy:** kolumna `tenant_id UUID` w każdej tabeli biznesowej, filtrowanie przez Hibernate `@TenantId` (patrz [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md))
- **Brak schema-per-tenant** - uzasadnienie w [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md) sekcja 1

---

## 2. Konwencje

### Naming

| Element | Konwencja | Przykład |
|---------|-----------|----------|
| Tabela | `snake_case`, **liczba mnoga** | `documents`, `chat_sessions`, `stored_files` |
| Kolumna | `snake_case` | `tenant_id`, `created_at`, `chunk_index` |
| Klucz obcy | `<tabela_referencowana_w_l_pojedynczej>_id` | `tenant_id`, `document_id`, `stored_file_id` |
| Indeks | `idx_<tabela>_<kolumny>` | `idx_documents_tenant_status` |
| Unique constraint | `uq_<tabela>_<kolumny>` | `uq_users_tenant_email` |
| Klucz obcy constraint | `fk_<tabela>_<docelowa>` | `fk_documents_tenants` |

### ID strategy

- **`UUID v7` (sortowalne, czas-bazowane)** generowane w aplikacji
- Kolumna `id UUID PRIMARY KEY` na każdej tabeli (bez `DEFAULT` - aplikacja dostarcza)
- W aplikacji: biblioteka `com.fasterxml.uuid:java-uuid-generator` lub Hibernate 6 `@UuidGenerator(style = TIME)` - decyzja na poziomie implementacji
- W domenie: każde ID to value object opakowujący UUID (`DocumentId`, `TenantId`, etc.)

### Audit columns

Każda tabela ma audit columns:

| Kolumna | Typ | NULL? | Co zawiera |
|---------|-----|-------|------------|
| `created_at` | `timestamptz` | NOT NULL | Hibernate `@CreatedDate` - czas wstawienia |
| `updated_at` | `timestamptz` | NOT NULL (oprócz immutable tabel) | Hibernate `@LastModifiedDate` |
| `created_by` | `uuid` | NULL | FK do `users.id` post-MVP. W MVP zawsze NULL. |
| `updated_by` | `uuid` | NULL | jw. |

**Wyjątki:**
- `stored_files`, `messages`, `chunks`, `event_publication` - immutable (no updates) - brak `updated_at`, `updated_by`
- `messages` - brak `updated_*` (wiadomość jest tworzona raz)
- `tenants` - sama dla siebie nie ma `created_by` (kto by go tworzył w pierwszym tenant?)

### Soft delete

**NIE używamy** - hard DELETE wszędzie. Kaskady FK zarządzają cleanup. Patrz tabela kaskad poniżej.

### Timestamps

- `timestamptz` (timestamp with time zone) na każdym czasie - **nie `timestamp`**
- W aplikacji: `Instant` (Java 8+ Time)
- ISO-8601 w JSON: `2025-05-22T08:15:30Z`

---

## 3. Multi-tenancy w schemacie

### Kolumna `tenant_id`

- `UUID NOT NULL` w **każdej** tabeli biznesowej (z wyjątkiem `tenants` samej + `event_publication`)
- FK: `REFERENCES tenants(id) ON DELETE RESTRICT`
- Spring Hibernate filtruje automatycznie przez `@TenantId`

### `ON DELETE RESTRICT` na FK do tenants

**Nie da się usunąć tenanta dopóki ma jakiekolwiek dane.** Zabezpieczenie:
- Operacja "usuń tenanta" wymaga świadomego cleanup wszystkich powiązanych rekordów
- Nie ma "ups, kasknął się tenant i z nim 10000 dokumentów"
- Post-MVP: osobny workflow "decommission tenant" który czyści w kontrolowanej kolejności

---

## 4. Flyway - organizacja migracji

### Struktura katalogów

```
multibot-backend/src/main/resources/db/migration/
├── common/
│   └── V20260522_0001__create_extension_pgvector.sql
│   └── V20260522_0002__create_extension_uuid.sql
├── tenant/
│   └── V20260522_0100__create_tenants.sql
│   └── V20260522_0101__seed_default_tenant.sql
├── user/
│   └── V20260522_0200__create_users.sql
├── file/
│   └── V20260522_0300__create_stored_files.sql
├── document/
│   └── V20260522_0400__create_documents.sql
├── indexing/
│   └── V20260522_0500__create_indexing_jobs.sql
├── vector/
│   └── V20260522_0600__create_chunks.sql
│   └── V20260522_0601__create_chunks_hnsw_index.sql
├── chat/
│   └── V20260522_0700__create_chat_sessions.sql
│   └── V20260522_0701__create_messages.sql
└── modulith/
    └── V20260522_0800__create_event_publication.sql
```

W `application.yml`:
```yaml
spring:
  flyway:
    locations:
      - classpath:db/migration/common
      - classpath:db/migration/tenant
      - classpath:db/migration/user
      - classpath:db/migration/file
      - classpath:db/migration/document
      - classpath:db/migration/indexing
      - classpath:db/migration/vector
      - classpath:db/migration/chat
      - classpath:db/migration/modulith
```

### Konwencja wersjonowania

`V<YYYYMMDD>_<seq>__<description>.sql`

- `YYYYMMDD` - data utworzenia migracji
- `seq` - kolejność w obrębie dnia (4-cyfrowa, z prefiksem dla modułu: 01xx=common, 01xx=tenant, 02xx=user, etc.)
- `description` - krótki opis (snake_case)

**Po co data + numer:** Flyway loaduje wszystkie migracje globalnie w kolejności wersji. Data zapewnia chronologiczność między modułami, numer w obrębie dnia - kolejność szczegółowa.

### Zasady migracji Flyway

1. **Nigdy nie modyfikuj migracji już zaaplikowanej** - tworzymy nową migrację z `ALTER TABLE`
2. **Każda migracja idempotentna** w obrębie siebie (idempotency w sensie "uruchom raz") - Flyway tego pilnuje przez checksum
3. **Brak `IF NOT EXISTS`** w `CREATE TABLE` - jeśli kolizja, chcemy błąd, nie milcząca zgoda
4. **Komentarz na górze pliku** - co i po co (1-2 linie)
5. **Migracje testów** w osobnym folderze `db/migration-test/` - seed data dla integration tests

---

## 5. Pgvector - setup

Pełne szczegóły indeksów, wymiarów, strategii similarity → [`pgvector.md`](pgvector.md).

W skrócie:
- Migracja `V20260522_0001__create_extension_pgvector.sql`: `CREATE EXTENSION IF NOT EXISTS vector;`
- Wymiar embeddingów: `1536` (OpenAI `text-embedding-3-small`)
- Distance: cosine (`<=>` operator)
- Index na `chunks.embedding`: HNSW (parametry → `pgvector.md`)

---

## 6. Tabele per moduł

### `organizations` (moduł `organization/`) — klient platformy

```sql
CREATE TABLE organizations (
    id                              UUID PRIMARY KEY,
    name                            VARCHAR(255) NOT NULL,
    slug                            VARCHAR(64)  NOT NULL UNIQUE,
    plan                            VARCHAR(32)  NOT NULL DEFAULT 'FREE',  -- FK billing_plans(code)
    status                          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    company_name                    VARCHAR(255),                          -- dane do faktur
    nip                             VARCHAR(20),
    monthly_messages_limit_override INTEGER,                               -- NULL = z planu
    max_bots_override               INTEGER,                               -- NULL = z planu (Enterprise = negocjowane)
    created_at / updated_at         TIMESTAMPTZ NOT NULL
);
```
Plan, limit wiadomości (wspólny dla wszystkich botów), limit liczby botów i dane firmowe są na poziomie **organizacji**.

### `tenants` (moduł `tenant/`) — = BOT (granica izolacji danych)

```sql
CREATE TABLE tenants (
    id              UUID         PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    slug            VARCHAR(64)  NOT NULL UNIQUE,
    organization_id UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at / updated_at / created_by / updated_by
);
CREATE INDEX idx_tenants_organization ON tenants(organization_id);
```

**Notatki (po pivocie Organizacja→Boty):**
- `tenants` = **bot**; `tenant_id` w pozostałych tabelach to id bota.
- `plan`, `company_name`, `nip`, override-y **przeniesione do `organizations`** (usunięte z `tenants`).
- Plany (`billing_plans.code`): `FREE` / `BASIC` / `PRO` / `ENTERPRISE`.

### `users` (moduł `auth/`)

```sql
CREATE TABLE users (
    id              UUID          PRIMARY KEY,
    email           VARCHAR(255)  NOT NULL UNIQUE,   -- login, globalnie unikalny
    password_hash   VARCHAR(255)  NOT NULL,          -- BCrypt
    role            VARCHAR(32)   NOT NULL,          -- SUPER_ADMIN | OWNER | ADMIN
    status          VARCHAR(32)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE | DISABLED | MUST_CHANGE_PASSWORD
    organization_id UUID,                            -- NULL dla SUPER_ADMIN; FK organizations
    created_by      UUID,
    created_at / updated_at / last_login_at,

    CONSTRAINT ck_users_org_for_role CHECK (
        (role = 'SUPER_ADMIN' AND organization_id IS NULL) OR
        (role IN ('OWNER','ADMIN') AND organization_id IS NOT NULL))
);
CREATE UNIQUE INDEX idx_users_one_owner_per_org ON users(organization_id) WHERE role = 'OWNER';
```

**Notatki:**
- User należy do **organizacji** (nie do pojedynczego bota) → zarządza wszystkimi botami org.
- `SUPER_ADMIN` jest ponad organizacjami (`organization_id` NULL).
- Email **globalnie unikalny** (jeden login = jedno konto).

### Pozostałe tabele dodane w Fazach A–D (skrót — DDL w migracjach)

| Tabela | Moduł / migracja | Po co | Kluczowe kolumny |
|--------|------------------|-------|------------------|
| `billing_plans` | billing | definicje planów (edytowalne w DB) | `code` PK, `monthly_messages_limit` (-1=∞), `max_bots`, `custom_domain_allowed`, `api_access_allowed`, `price_pln_monthly` |
| `tenant_usage` | billing | licznik zużycia **per bot** per miesiąc | PK `(tenant_id, period_month)`, `user_messages`, `ai_messages`, `tokens_used`, `cost_usd` |
| `audit_log` | auth | log zdarzeń bezpieczeństwa | — |
| `spring_session` / `spring_session_attributes` | auth | sesje Spring Session JDBC (cookie auth) | — |
| `widget_settings` | widget | konfiguracja widgetu bota (`@TenantId`) | kolor, tytuł, powitanie, isActive |
| `widget_keys` | widget | klucze publiczne widgetu + `allowed_origins TEXT[]` | `key`, `is_active`, `allowed_origins` |
| `chatbot_versions` | botversion (document) | wersjonowanie bota (deep-copy) | `status` DRAFT/ACTIVE |
| `chatbot_active_version` | botversion | aktywna wersja per bot | PK `tenant_id` |
| `tenant_rag_settings` | rag | parametry RAG per bot (`@TenantId`) | top-k, próg podobieństwa, model |

Limit wiadomości jest **pooled** na organizację: sprawdzany jako suma `tenant_usage.user_messages` wszystkich botów org vs limit planu (override z `organizations`).

### `stored_files` (moduł `file/`)

```sql
CREATE TABLE stored_files (
    id                  UUID           PRIMARY KEY,
    tenant_id           UUID           NOT NULL,
    original_filename   VARCHAR(512)   NOT NULL,
    mime_type           VARCHAR(128)   NOT NULL,
    size_bytes          BIGINT         NOT NULL,
    storage_key         VARCHAR(1024)  NOT NULL,
    created_at          TIMESTAMPTZ    NOT NULL,
    created_by          UUID,

    CONSTRAINT fk_stored_files_tenants FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT ck_stored_files_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_stored_files_tenant ON stored_files(tenant_id);
```

**Notatki:**
- `storage_key` - nieprzezroczysty identyfikator. Format zależy od adaptera (`local/path/to/file.pdf`, `s3://bucket/key`)
- Immutable: brak `updated_at`/`updated_by` - plik raz zapisany jest stały (nowa wersja = nowy rekord)
- Brak FK z documents tutaj - relacja documents → stored_files

### `documents` (moduł `document/`)

```sql
CREATE TABLE documents (
    id              UUID           PRIMARY KEY,
    tenant_id       UUID           NOT NULL,
    stored_file_id  UUID           NOT NULL,
    filename        VARCHAR(512)   NOT NULL,
    mime_type       VARCHAR(128)   NOT NULL,
    size_bytes      BIGINT         NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    uploaded_at     TIMESTAMPTZ    NOT NULL,
    indexed_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    created_by      UUID,
    updated_by      UUID,

    CONSTRAINT fk_documents_tenants FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_documents_stored_files FOREIGN KEY (stored_file_id) REFERENCES stored_files(id) ON DELETE RESTRICT,
    CONSTRAINT ck_documents_status CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX idx_documents_tenant_status ON documents(tenant_id, status);
CREATE INDEX idx_documents_tenant_uploaded ON documents(tenant_id, uploaded_at DESC);
CREATE INDEX idx_documents_stored_file ON documents(stored_file_id);
```

**Notatki:**
- `filename` może się różnić od `stored_files.original_filename` (user może zmienić nazwę w UI - post-MVP)
- `uploaded_at` osobno od `created_at` - może być rozróżnienie (np. import historyczny)
- `indexed_at` NULL dopóki nie ukończono indeksacji
- Composite index `(tenant_id, status)` - dla query "wszystkie ready dokumenty"
- Composite index `(tenant_id, uploaded_at DESC)` - dla listy najnowszych

### `indexing_jobs` (moduł `indexing/`)

```sql
CREATE TABLE indexing_jobs (
    id              UUID           PRIMARY KEY,
    tenant_id       UUID           NOT NULL,
    document_id     UUID           NOT NULL,
    status          VARCHAR(32)    NOT NULL,
    error_message   TEXT,
    chunks_created  INT,
    started_at      TIMESTAMPTZ    NOT NULL,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,

    CONSTRAINT fk_indexing_jobs_tenants FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_indexing_jobs_documents FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT ck_indexing_jobs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_indexing_jobs_document ON indexing_jobs(document_id);
CREATE INDEX idx_indexing_jobs_tenant_status ON indexing_jobs(tenant_id, status);
```

**Notatki:**
- `ON DELETE CASCADE` na `document_id` - jak usuwam dokument, jego joby znikają
- `error_message TEXT` - może być długi stack trace
- Brak `created_by`/`updated_by` - jobs są system-generated

### `chunks` (moduł `vector/`) - z pgvector

```sql
CREATE TABLE chunks (
    id            UUID         PRIMARY KEY,
    tenant_id     UUID         NOT NULL,
    document_id   UUID         NOT NULL,
    chunk_index   INT          NOT NULL,
    content       TEXT         NOT NULL,
    embedding     VECTOR(1536) NOT NULL,
    metadata      JSONB,
    created_at    TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_chunks_tenants FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_chunks_documents FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE,
    CONSTRAINT uq_chunks_document_index UNIQUE (document_id, chunk_index)
);

CREATE INDEX idx_chunks_tenant_document ON chunks(tenant_id, document_id);
CREATE INDEX idx_chunks_metadata ON chunks USING GIN (metadata);

-- HNSW index na embeddingach (szczegóły w pgvector.md)
CREATE INDEX idx_chunks_embedding ON chunks USING HNSW (embedding vector_cosine_ops);
```

**Notatki:**
- `embedding VECTOR(1536)` - rozmiar dla `text-embedding-3-small`
- `metadata JSONB` - elastyczne pole: `{"page": 3, "section": "Wstęp", "source_filename": "owu.pdf"}`
- Index GIN na metadata dla query typu `WHERE metadata ->> 'page' = '3'`
- `ON DELETE CASCADE` na document_id - usunięcie dokumentu kasknie chunki
- Unique `(document_id, chunk_index)` - chronologia chunków w dokumencie
- Immutable: tylko `created_at`, brak `updated_at`

### `chat_sessions` (moduł `chat/`)

```sql
CREATE TABLE chat_sessions (
    id                    UUID          PRIMARY KEY,
    tenant_id             UUID          NOT NULL,
    end_user_external_id  VARCHAR(255),
    anonymous_id          VARCHAR(255)  NOT NULL,
    started_at            TIMESTAMPTZ   NOT NULL,
    last_activity_at      TIMESTAMPTZ   NOT NULL,
    metadata              JSONB,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,

    CONSTRAINT fk_chat_sessions_tenants FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT
);

CREATE INDEX idx_chat_sessions_tenant_activity ON chat_sessions(tenant_id, last_activity_at DESC);
CREATE INDEX idx_chat_sessions_tenant_external ON chat_sessions(tenant_id, end_user_external_id) WHERE end_user_external_id IS NOT NULL;
CREATE INDEX idx_chat_sessions_anonymous ON chat_sessions(anonymous_id);
```

**Notatki:**
- `anonymous_id` - zawsze obecny (UUID z cookie / sessionStorage)
- `end_user_external_id` - opcjonalny (jeśli klient identyfikuje swoich userów: `acme-customer-123`)
- `last_activity_at` - update'owane przy każdej nowej wiadomości (dla sortowania "ostatnio aktywne")
- Partial index na `end_user_external_id` - tylko gdzie NOT NULL (oszczędność miejsca)
- `metadata` - opcjonalne: kontekst od klienta (URL strony, IP, user agent - po-MVP)

### `messages` (moduł `chat/`)

```sql
CREATE TABLE messages (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    session_id  UUID         NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    content     TEXT         NOT NULL,
    sources     JSONB,
    tokens_used INT,
    created_at  TIMESTAMPTZ  NOT NULL,

    CONSTRAINT fk_messages_tenants FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE RESTRICT,
    CONSTRAINT fk_messages_sessions FOREIGN KEY (session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE,
    CONSTRAINT ck_messages_role CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM'))
);

CREATE INDEX idx_messages_session_created ON messages(session_id, created_at);
CREATE INDEX idx_messages_tenant_created ON messages(tenant_id, created_at DESC);
```

**Notatki:**
- Immutable - brak `updated_*`
- `sources JSONB` - lista cytatów: `[{"chunkId": "...", "documentId": "...", "snippet": "...", "score": 0.85}, ...]`
- `tokens_used` - tylko dla `role=ASSISTANT` (cost tracking per tenant - post-MVP)
- `ON DELETE CASCADE` na `session_id` - usunięcie sesji kasknie wiadomości
- Composite index `(session_id, created_at)` - dla query "historia w sesji"
- Composite index `(tenant_id, created_at DESC)` - dla query "ostatnie wiadomości tenant" (analytics)

### `event_publication` (Spring Modulith)

```sql
CREATE TABLE event_publication (
    id                UUID         PRIMARY KEY,
    listener_id       VARCHAR(512) NOT NULL,
    event_type        VARCHAR(512) NOT NULL,
    serialized_event  VARCHAR      NOT NULL,
    publication_date  TIMESTAMPTZ  NOT NULL,
    completion_date   TIMESTAMPTZ
);

CREATE INDEX idx_event_publication_incomplete ON event_publication(publication_date) WHERE completion_date IS NULL;
```

**Notatki:**
- Schemat narzucony przez Spring Modulith (możemy dorzucić migrację ręcznie albo zaufać auto-create)
- **Brak FK do innych tabel** - event nie należy do tenanta na poziomie schematu (tenant_id siedzi w `serialized_event` JSON)
- Partial index na nieukończonych - szybkie znalezienie "pending events" przy restart aplikacji

---

## 7. Diagram ERD

**Diagram:** [`../diagrams/db-schema.puml`](../diagrams/db-schema.puml)

Pokazuje pełen schemat z kolorystyką:
- Szare: tabele biznesowe
- Fioletowe: Spring Modulith
- Klucze prymarne wyróżnione
- Kaskady oznaczone na relacjach

---

## 8. Kaskady - mapa

| Relacja | Kaskada | Uzasadnienie |
|---------|---------|-------------|
| tenants → wszystkie | `RESTRICT` | Bezpieczeństwo: nie da się usunąć tenanta z danymi |
| stored_files ← documents | `RESTRICT` | Aplikacja świadomie usuwa oba w transakcji |
| documents → chunks | `CASCADE` | Chunki nie mają sensu bez dokumentu |
| documents → indexing_jobs | `CASCADE` | Joby nie mają sensu bez dokumentu |
| chat_sessions → messages | `CASCADE` | Wiadomości nie mają sensu bez sesji |
| users → tenants | `RESTRICT` (post-MVP) | jw. dla bezpieczeństwa |

---

## 9. Seed data

Migracja `V20260522_0101__seed_default_tenant.sql`:

```sql
-- Default tenant dla MVP (tenant_id=1 hardcoded w aplikacji)
INSERT INTO tenants (id, name, slug, plan, status, created_at, updated_at)
VALUES (
    '00000000-0000-7000-8000-000000000001',  -- znany UUID v7 dla default tenanta
    'Default Tenant',
    'default',
    'FREE',
    'ACTIVE',
    NOW(),
    NOW()
);
```

**Uwaga:** UUID `00000000-0000-7000-8000-000000000001` jest "zarezerwowany" dla default tenanta MVP. Hardcoded w `MockTenantResolver`. Po włączeniu prawdziwego multi-tenant - default tenant pozostaje (dla migracji starych danych).

---

## 10. Konwencje migracji - co MOŻNA, czego NIE

### MOŻNA

- Dodawać nowe tabele
- Dodawać kolumny (z `DEFAULT` lub `NULL`)
- Dodawać indeksy
- Modyfikować dane (`INSERT`, `UPDATE`, `DELETE`)
- Tworzyć nowe migracje z `ALTER TABLE`

### NIE WOLNO

- **Modyfikować istniejących migracji** - Flyway pamięta checksum
- **Usuwać migracji** - jw.
- Robić destrukcyjnych zmian bez backupu w produkcji (`DROP COLUMN`, `DROP TABLE`)
- `IF NOT EXISTS` / `IF EXISTS` - chcemy widzieć błąd jak coś jest źle
- Mieszać DDL i DML w jednym pliku jeśli można ich rozdzielić

### Workflow dla zmiany schematu

1. Twórz nowy plik `V<data>_<seq>__<opis>.sql`
2. Pisz `ALTER TABLE` lub `CREATE INDEX` zamiast modyfikować poprzednie migracje
3. Test lokalnie z czystą bazą (`docker-compose down -v && docker-compose up`)
4. Aktualizuj JPA entity + EntityMapper jeśli zmiana kolumny
5. Aktualizuj testy

---

## 11. Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Szczegóły pgvector: indeksy HNSW, similarity search | [`pgvector.md`](pgvector.md) |
| Dlaczego shared-DB multi-tenancy a nie schema-per-tenant | [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md) sekcja 1 |
| Strategia izolacji tenantów w kodzie | [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md) |
| Mapping JPA entities ↔ domain (w którym pakiecie) | [`../architecture/backend/hexagonal-pattern.md`](../architecture/backend/hexagonal-pattern.md) |
| Moduły biznesowe (które modele domenowe pasują do których tabel) | [`../architecture/backend/modules-business.md`](../architecture/backend/modules-business.md) |
