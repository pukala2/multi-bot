# REST API - przegląd i konwencje

> **Aktualizacja (po Fazach A–D + pivot Organizacja→Boty):** auth/billing/super-admin/bots są już zaimplementowane.
> Uwierzytelnianie to **sesje (cookie) + CSRF**, nie JWT (patrz sekcja 10). Endpointy `documents`, `chat`,
> `admin/widget`, `admin/versions`, `admin/rag-settings`, `admin/analytics`, `admin/stats`, `widget/config`
> są **generowane z `openapi.yaml`** (kontrolery implementują interfejsy). Endpointy `auth`, `bots`,
> `super-admin`, `admin/billing`, `widget/turnstile` to **ręczne kontrolery** (poza `openapi.yaml`).

Strategia API-first: **`openapi.yaml` jest single source of truth** dla części generowanej. Z niego generujemy DTOs i API interfaces w buildzie.

Ten dokument:
- Strategia i workflow API-first
- Organizacja plików yaml (composition przez `$ref`)
- Konwencje (paths, HTTP methods, status codes, error format)
- Wspólne komponenty
- Lista endpointów MVP (high-level)
- Versioning, authentication, streaming

**Szczegółowe definicje endpointów** (z DTOs, examples, validations) → [`openapi.yaml`](openapi.yaml) i pliki w `documents/`, `chat/`, `_shared/`.

---

## 1. Strategia API-first

### Workflow

```
1. Edycja openapi.yaml (zmiana kontraktu)
        ↓
2. Maven build: openapi-generator-maven-plugin
        ↓
3. Generuje:
   - DTO records w com.multibot.api.generated.model.*
   - API interfaces w com.multibot.api.generated.api.*
        ↓
4. Controller implements wygenerowany interfejs
        ↓
5. Kompilator failuje jeśli controller nie pasuje do interfejsu
        ↓
6. Frontend / Widget generują TypeScript klienta z tego samego yamla
```

### Co dostajemy

- **Niemożliwy do uniknięcia rozjazd kontraktu i kodu** - kompilator failuje gdy controller nie pasuje
- **Swagger UI za darmo** - SpringDoc auto-renderuje yamla
- **Frontend independence** - frontend ma kontrakt, może mockować przed BE
- **Versioning history** - diff yamla pokazuje breaking changes
- **Postman / curl examples** - auto-generowane z spec'u

### Co kosztuje

- Yaml jest verbose - 50 linii na nietrywialny endpoint
- Generated code w `target/generated-sources/` - nie commitujemy, ale obecny w buildzie
- Cykl: yaml → build → kod (każda zmiana = przegenerowanie)
- Generowane DTO są w jednym pakiecie (`com.multibot.api.generated.model`), nie per moduł - musimy żyć z tym kompromisem

---

## 2. Organizacja plików yaml

### Struktura

```
docs/api/
├── endpoints.md              ← ten plik
└── openapi.yaml              ← cały spec w jednym pliku
```

### Dlaczego jeden plik

Próbowaliśmy podziału per moduł z `$ref` między plikami (`documents/api.yaml`, `chat/api.yaml`, `_shared/components.yaml`).

**Problem:** preview w IntelliJ / Swagger Editor nie potrafi resolveować `$ref` przez wiele plików - rzuca "Could not resolve reference". Spec był poprawny (`openapi-generator` i SpringDoc by sobie poradziły), ale developerska "loop" zepsuta.

Decyzja: jeden monolityczny `openapi.yaml`. Przy ~10 endpointach MVP plik ma ~700 linii - czytalny. Organizacja przez **sekcje komentarzy** w pliku:
- `# ----- Documents -----`
- `# ----- Chat -----`
- `# === Shared (common) ===`
- `# === Documents ===`
- `# === Chat ===`

Plus `tags` (OpenAPI) grupują endpointy w Swagger UI.

### Gdy plik urośnie (post-MVP)

Jeśli osiągniemy 30+ endpointów i plik staje się nieczytelny - wtedy bundling:

```bash
npm install -g @redocly/cli

# Edytujemy modular yamls, bundle generuje single openapi-bundled.yaml
redocly bundle docs/api/src/openapi.yaml -o docs/api/openapi.yaml
```

`openapi.yaml` w git = bundled. `src/` = modular source. Wtedy:
- Edytujesz modular
- Bundle jako "build step" przed commitem (lub w pre-commit hooku)
- Preview w IntelliJ ładuje już zbundlowany plik

Ale to **post-MVP** decyzja. W MVP - jeden plik, koniec.

---

## 3. Maven generator config (wstępny)

W przyszłym `backend/pom.xml`:

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.x</version>
    <executions>
        <execution>
            <id>generate-multibot-api</id>
            <phase>generate-sources</phase>
            <goals><goal>generate</goal></goals>
            <configuration>
                <inputSpec>${project.basedir}/../docs/api/openapi.yaml</inputSpec>
                <generatorName>spring</generatorName>
                <apiPackage>com.multibot.api.generated.api</apiPackage>
                <modelPackage>com.multibot.api.generated.model</modelPackage>
                <invokerPackage>com.multibot.api.generated</invokerPackage>
                <configOptions>
                    <interfaceOnly>true</interfaceOnly>
                    <useSpringBoot3>true</useSpringBoot3>
                    <useJakartaEe>true</useJakartaEe>
                    <useTags>true</useTags>
                    <openApiNullable>false</openApiNullable>
                    <useBeanValidation>true</useBeanValidation>
                    <dateLibrary>java8</dateLibrary>
                </configOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Kluczowe opcje:
- `interfaceOnly=true` - generujemy tylko interfejsy + modele, nie stub controllery
- `useTags=true` - grupowanie endpointów per tag (= per moduł)
- `useBeanValidation=true` - generuje adnotacje Jakarta Validation
- `openApiNullable=false` - bez `JsonNullable` wrappera, tylko nullable types

DTO będą jako **klasy** (nie records) domyślnie w spring generator - records wymagają osobnej konfiguracji `useRecord=true` (dostępne w OpenAPI Generator 7.x+).

---

## 4. Konwencje API

### Paths

| Reguła | Przykład |
|--------|----------|
| Liczba mnoga, kebab-case | `/api/documents`, `/api/chat-sessions` |
| Hierarchiczne dla zagnieżdżonych zasobów | `/api/chat/sessions/{id}/messages` |
| ID w ścieżce, nie w query string | `/api/documents/{id}` (NIE `/api/documents?id=42`) |
| Akcje jako sub-path tylko gdy konieczne | `/api/documents/{id}/retry-indexing` (post-MVP) |
| Prefiks `/api/` dla całego REST API | wszystko |
| `/api/admin/*` dla operacji zalogowanego admina klienta (per aktywny bot) | `/api/admin/billing/usage`, `/api/admin/stats` |
| `/api/super-admin/*` dla operacji platformy (rola SUPER_ADMIN) | `/api/super-admin/organizations` |
| `/api/widget/*` dla publicznych endpointów widgetu | `/api/widget/config`, `/api/widget/turnstile` |

### HTTP Methods

| Metoda | Kiedy | Idempotent? | Body? |
|--------|-------|-------------|-------|
| `GET` | Odczyt zasobu lub kolekcji | Tak | Nie |
| `POST` | Utworzenie zasobu (lub akcja bez naturalnego ID) | Nie | Tak |
| `PUT` | Pełne zastąpienie zasobu | Tak | Tak |
| `PATCH` | Częściowa modyfikacja | Tak | Tak |
| `DELETE` | Usunięcie | Tak | Nie |

W MVP używamy głównie GET/POST/DELETE. PATCH dorzucamy gdy będzie potrzeba update.

### Status codes

| Kod | Kiedy używamy |
|-----|---------------|
| **200 OK** | GET zwracający dane, POST/PUT/PATCH gdy zwracamy zmodyfikowany zasób |
| **201 Created** | POST tworzący nowy zasób (+ `Location` header z URL nowego zasobu) |
| **202 Accepted** | POST wyzwalający async operację (np. upload dokumentu - indeksacja w tle) |
| **204 No Content** | DELETE sukces, PATCH sukces bez zwracania body |
| **400 Bad Request** | Walidacja danych wejściowych failed (Jakarta Validation) |
| **401 Unauthorized** | Brak / nieprawidłowy token (post-MVP) |
| **403 Forbidden** | Token OK, ale brak uprawnień do zasobu (cross-tenant access) |
| **404 Not Found** | Zasób nie istnieje (lub należy do innego tenanta - patrz uwaga niżej) |
| **402 Payment Required** | Limit wiadomości organizacji przekroczony (czat) — `MESSAGE_LIMIT_REACHED` |
| **409 Conflict** | Konflikt biznesowy (duplicate email, limit liczby botów planu przekroczony) |
| **413 Payload Too Large** | Plik większy niż 50MB |
| **500 Internal Server Error** | Bug serwera, exception nieprzewidziany |

**Uwaga 404 vs 403:** dla bezpieczeństwa preferujemy **404** dla "zasób należy do innego tenanta" (nie zdradzamy istnienia). Patrz [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md) sekcja 7.

### Error format - RFC 7807 ProblemDetail

Wszystkie błędy 4xx i 5xx zwracają JSON w formacie [RFC 7807](https://www.rfc-editor.org/rfc/rfc7807):

```json
{
  "type": "https://multibot.app/errors/document-not-found",
  "title": "Document Not Found",
  "status": 404,
  "detail": "Document with id 42 not found",
  "instance": "/api/documents/42",
  "code": "DOCUMENT_NOT_FOUND",
  "tenantId": "00000000-0000-7000-8000-000000000001",
  "traceId": "abc-123-def-456",
  "timestamp": "2025-05-22T08:15:30Z"
}
```

Pełne uzasadnienie i wszystkie pola → [`../architecture/backend/operations.md`](../architecture/backend/operations.md) sekcja 4.

Schema `ProblemDetail` jest w `_shared/components.yaml` i $ref'owana z każdego endpointu.

### Validation errors (400)

Dodatkowo do ProblemDetail, dla walidacji pole `errors`:

```json
{
  "type": "https://multibot.app/errors/validation-failed",
  "title": "Validation Failed",
  "status": 400,
  "errors": [
    { "field": "filename", "message": "must not be blank" },
    { "field": "file", "message": "size exceeds 50MB" }
  ]
}
```

---

## 5. Pagination

Wszystkie endpointy listujące zasoby (`GET /api/documents`, `GET /api/chat/sessions`) wspierają paginację.

### Request - query params

| Param | Opis | Default |
|-------|------|---------|
| `page` | Numer strony (0-based) | 0 |
| `size` | Rozmiar strony | 20 |
| `sort` | Pole sortowania + kierunek (`field,asc` lub `field,desc`) | per endpoint |

Przykład: `GET /api/documents?page=0&size=10&sort=uploadedAt,desc`

### Response - struktura

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5,
  "first": true,
  "last": false
}
```

Schemat `PaginatedResponse<T>` w `_shared/components.yaml`. OpenAPI obsługuje generyki przez `allOf` z dziedziczeniem (właściwie discriminator + composition).

W praktyce yaml definiuje konkretne typy: `PaginatedDocumentResponse`, `PaginatedChatSessionResponse`.

---

## 6. Tagi - organizacja per moduł

OpenAPI tags grupują endpointy w Swagger UI. Każdy tag = jeden moduł:

| Tag | Moduł | Endpointy MVP | Endpointy post-MVP |
|-----|-------|---------------|-------------------|
| `Documents` | `document/` | ✅ 5 endpointów | retry-indexing |
| `Chat` | `chat/` | ✅ 4 endpointy | (możliwe: search) |
| `Auth` | `user/` | - | login, register, refresh |
| `Admin` | `tenant/` + `user/` | - | tenants, users, stats |
| `Widget` | `chat/` (alternate auth) | - | sessions, messages dla widgetu |

W MVP yaml zawiera tylko **Documents** i **Chat**.

---

## 7. Wspólne komponenty (`_shared/components.yaml`)

### Schemas

| Schema | Opis |
|--------|------|
| `ProblemDetail` | RFC 7807 error response |
| `ValidationErrorDetail` | element listy `errors` w 400 |
| `PageRequest` | parametry paginacji (rozkładany na query params, nie JSON body) |
| `IdResponse` | response zawierający tylko `id` (po POST tworzącym zasób) |

### Parameters (reusable)

| Parameter | Opis |
|-----------|------|
| `PageParam` | `page` query param |
| `SizeParam` | `size` query param |
| `SortParam` | `sort` query param |

### Responses (reusable)

| Response | Opis |
|----------|------|
| `BadRequest` | 400 z ProblemDetail + ValidationErrorDetail |
| `Unauthorized` | 401 z ProblemDetail (post-MVP) |
| `Forbidden` | 403 z ProblemDetail |
| `NotFound` | 404 z ProblemDetail |
| `Conflict` | 409 z ProblemDetail |
| `PayloadTooLarge` | 413 z ProblemDetail |
| `InternalServerError` | 500 z ProblemDetail |

Każdy endpoint w yaml referuje te response'y zamiast definiować inline:
```yaml
responses:
  '404':
    $ref: '../_shared/components.yaml#/components/responses/NotFound'
```

---

## 8. Lista endpointów

### Auth + sesja (ręczny kontroler, poza openapi.yaml)

| Method | Path | Dostęp | Notatki |
|--------|------|--------|---------|
| `POST` | `/api/auth/login` | publiczny | email+hasło → ustawia sesję; zwraca usera + `organizationId` + `activeBot` + `bots[]` |
| `POST` | `/api/auth/logout` | publiczny | czyści sesję (204) |
| `GET` | `/api/auth/me` | zalogowany | user + organizacja + aktywny bot + lista botów |
| `POST` | `/api/auth/active-bot` | OWNER/ADMIN | `{tenantId}` — przełącza aktywnego bota (waliduje że bot należy do org usera; obcy → 403) |

### Self-serve boty (ręczny kontroler) — rola OWNER

| Method | Path | Sukces | Notatki |
|--------|------|--------|---------|
| `GET` | `/api/bots/limit` | 200 | `{allowed, currentBots, maxBots, unlimited}` dla org usera |
| `POST` | `/api/bots` | 201 | tworzy bota w org usera; egzekwuje `max_bots` planu (409 + upsell po limicie) |

### Super admin (ręczny kontroler) — rola SUPER_ADMIN

| Method | Path | Sukces | Notatki |
|--------|------|--------|---------|
| `GET` | `/api/super-admin/organizations` | 200 | lista organizacji + ich boty + wspólne zużycie + limit |
| `POST` | `/api/super-admin/organizations` | 201 | provisioning: organizacja + owner (auto-hasło) + pierwszy bot; hasło/klucz pokazane RAZ |
| `POST` | `/api/super-admin/organizations/{orgId}/bots` | 201 | dodaje bota do organizacji (bez limitu planu) |

### Billing — admin klienta (ręczny kontroler)

| Method | Path | Sukces | Notatki |
|--------|------|--------|---------|
| `GET` | `/api/admin/billing/usage` | 200 | zużycie wiadomości (pooled = suma botów org) vs limit; bez tokenów/kosztów |
| `GET` | `/api/admin/billing/plan` | 200 | plan organizacji + cechy (max_bots, custom domain, API access) |

### Documents (generowane z openapi.yaml)

| Method | Path | Tag | Use case | Sukces | Notatki |
|--------|------|-----|----------|--------|---------|
| `POST` | `/api/documents` | Documents | `UploadDocumentUseCase` | **202** | multipart/form-data, wyzwala async indeksację |
| `GET` | `/api/documents` | Documents | `ListDocumentsUseCase` | 200 | paginacja, sort default `uploadedAt,desc` |
| `GET` | `/api/documents/{id}` | Documents | `GetDocumentUseCase` | 200 | pełne dane dokumentu |
| `GET` | `/api/documents/{id}/status` | Documents | `GetDocumentStatusUseCase` | 200 | lekki endpoint do pollowania, zwraca tylko status |
| `DELETE` | `/api/documents/{id}` | Documents | `DeleteDocumentUseCase` | 204 | kaskadowo: chunki + plik |

### Chat

| Method | Path | Tag | Use case | Sukces | Notatki |
|--------|------|-----|----------|--------|---------|
| `POST` | `/api/chat/sessions` | Chat | `StartSessionUseCase` | 201 | + `Location: /api/chat/sessions/{id}` |
| `GET` | `/api/chat/sessions` | Chat | `ListSessionsUseCase` | 200 | paginacja, sort default `lastActivityAt,desc` |
| `GET` | `/api/chat/sessions/{id}/messages` | Chat | `GetSessionHistoryUseCase` | 200 | paginacja, sort default `createdAt,asc` |
| `POST` | `/api/chat/sessions/{id}/messages` | Chat | `SendMessageUseCase` | 200 | SSE stream lub JSON; **402** gdy limit org przekroczony |

### Admin — panel klienta, per aktywny bot (generowane z openapi.yaml)

| Method | Path | Sukces | Notatki |
|--------|------|--------|---------|
| `GET` / `PUT` | `/api/admin/widget` | 200 | konfiguracja widgetu aktywnego bota (kolor, tytuł, powitanie…) |
| `GET` | `/api/admin/versions` + `/active` | 200 | wersje bota; `POST /api/admin/versions`, `POST /{id}/activate`, `DELETE /{id}` |
| `GET` / `PUT` | `/api/admin/rag-settings` | 200 | ustawienia RAG aktywnego bota |
| `GET` | `/api/admin/analytics` | 200 | analityka bota (tokeny/koszty tylko dla super admina) |
| `GET` | `/api/admin/stats` | 200 | KPI dashboardu (wymaga aktywnego bota → SUPER_ADMIN 500/ukryte w UI) |

### Widget — publiczny (osadzony na stronie klienta)

| Method | Path | Dostęp | Notatki |
|--------|------|--------|---------|
| `GET` | `/api/widget/config` | publiczny (`?key=`) | konfiguracja widgetu po kluczu publicznym |
| `GET` | `/api/widget/turnstile` | publiczny | `{enabled, siteKey}` dla anty-bota (ręczny kontroler) |

### Health / docs (Spring Boot)

| Method | Path | Notatki |
|--------|------|---------|
| `GET` | `/actuator/health` + `/health/**` | publiczny (liveness/readiness dla probe'ów) |
| `GET` | `/swagger-ui.html`, `/v3/api-docs` | SpringDoc (tylko dev) |

---

## 9. Streaming (SSE) - jak opisujemy w OpenAPI

`POST /api/chat/sessions/{id}/messages` zwraca **Server-Sent Events stream**, nie pojedynczy JSON.

W OpenAPI 3.1 SSE opisujemy jako response z `text/event-stream`:

```yaml
responses:
  '200':
    description: SSE stream odpowiedzi
    content:
      text/event-stream:
        schema:
          type: string
          description: |
            Strumień eventów SSE w formacie:

            event: chunk
            data: {"text": "część odpowiedzi..."}

            event: sources
            data: {"sources": [...]}

            event: done
            data: {"messageId": "uuid", "tokensUsed": 1234}
```

**Ograniczenie:** OpenAPI nie wspiera natywnie schemy dla pojedynczych SSE events. Pozostają jako "string" w spec, schemat eventów dokumentujemy w `description`. Klient TypeScript musi sam parsować.

W praktyce dla MVP - opisujemy w description, schemy konkretnych event payloadów (`MessageChunkEvent`, `MessageSourcesEvent`, `MessageDoneEvent`) trzymamy jako schemas w komponentach, nawet jeśli OpenAPI ich tu nie używa.

---

## 10. Uwierzytelnianie — sesje (cookie) + CSRF

**NIE JWT.** Spring Security 6.4 + Spring Session JDBC (sesje w Postgres, przeżywają restart). Panel admina jest same-origin, więc sesja w cookie (HttpOnly, SameSite=Lax) jest prostsza i bezpieczniejsza niż JWT (natychmiastowy logout, brak refresh-token).

- **Logowanie:** `POST /api/auth/login` (email+hasło) → cookie sesji `SESSION`.
- **CSRF:** mutacje wymagają nagłówka `X-XSRF-TOKEN` zgodnego z cookie `XSRF-TOKEN` (double-submit, `CookieCsrfTokenRepository`). Frontend dokleja go automatycznie. CSRF-exempt: `/api/widget/**`, `/api/chat/**`, `/actuator/**`.
- **Role:** `SUPER_ADMIN` (platforma, bez organizacji), `OWNER`, `ADMIN` (per organizacja).
- **Autoryzacja ścieżek:** `/api/super-admin/**` → `SUPER_ADMIN`; `/api/bots/**` → `OWNER`; `/api/auth/login|logout`, `/api/widget/**`, `POST /api/chat/sessions[/…/messages]`, `/actuator/health/**`, `/actuator/info` → publiczne; reszta → zalogowany.
- **Tenant/aktywny bot:** rozwiązywany z sesji (patrz [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md) sekcja 3), nie z tokenu.
- **Widget (anonim):** osobny mechanizm — `X-Multibot-Widget-Key` + Origin + rate-limit + Turnstile (`WidgetGuardFilter`), nie sesja.

> Endpointy auth/bots/super-admin/billing są ręcznymi kontrolerami i nie figurują w `openapi.yaml`.

---

## 11. Versioning

### Polityka

- W MVP brak wersjonowania w URL (`/api/documents`, nie `/api/v1/documents`)
- Breaking change → `/api/v2/documents` + utrzymujemy `/api/v1/...` przez pewien okres deprecation
- "Breaking change" = zmiana wymagająca update kodu klienta:
  - Usunięcie endpointu / pola
  - Zmiana typu pola
  - Zmiana semantyki status code
  - Dodanie required field do request body

### Co NIE jest breaking change

- Dodanie nowego endpointu
- Dodanie optional field do response
- Dodanie optional query param

Te zmiany są **backwards-compatible** - klienci pasywnie ignorują nowe pola.

### OpenAPI version w spec

`info.version` w yaml = wersja Multibot API (semver):
- `1.0.0` - pierwszy stable release
- `1.1.0` - dodanie endpointów
- `2.0.0` - breaking changes

---

## 12. Konwencje yaml

### Naming w schemas

| Element | Konwencja |
|---------|-----------|
| Schema | `PascalCase` (np. `Document`, `UploadDocumentRequest`) |
| Field | `camelCase` (np. `uploadedAt`, `tenantId`) |
| Enum value | `SCREAMING_SNAKE_CASE` (np. `PENDING`, `READY`) |
| Operation ID | `<verb><Noun>` (np. `listDocuments`, `uploadDocument`) - z tego nazwa metody w generated kodzie |
| Tag | `PascalCase` (np. `Documents`, `Chat`) |

### Examples

Każdy schema ma sekcję `example` z realistycznym przykładem:
```yaml
Document:
  type: object
  properties:
    id:
      type: string
      format: uuid
      example: "01918a3f-9c1e-7c40-8000-000000000001"
    filename:
      type: string
      example: "owu-ubezpieczenie-2025.pdf"
```

Examples są używane w Swagger UI i generatorach klientów.

### Validation

Używamy OpenAPI walidacji:
- `required: [...]`
- `minLength`, `maxLength` dla stringów
- `minimum`, `maximum` dla liczb
- `pattern` dla regex
- `format: uuid`, `format: date-time`, `format: email`

Generator generuje adnotacje Jakarta Validation (`@NotNull`, `@Size`, `@Pattern`) na DTO.

---

## 13. Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Pełny spec OpenAPI (wszystko w jednym pliku) | [`openapi.yaml`](openapi.yaml) |
| Format błędów - pełne uzasadnienie | [`../architecture/backend/operations.md`](../architecture/backend/operations.md) sekcja 4 |
| Moduły biznesowe + ich use case'y | [`../architecture/backend/modules-business.md`](../architecture/backend/modules-business.md) |
| Multi-tenancy w endpointach | [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md) |
| Streaming chatu - flow | [`../flows/chat-rag.md`](../flows/chat-rag.md) |
