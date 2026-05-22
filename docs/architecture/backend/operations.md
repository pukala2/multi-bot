# Operacje: konfiguracja, błędy, testy

Praktyczne sprawy:
- konfiguracja Spring (`application.yml`, profile, env vars)
- obsługa błędów (hierarchia wyjątków, ControllerAdvice, format JSON)
- logging i obserwowalność
- strategia testów

---

## 1. Konfiguracja Spring

### Struktura plików

```
backend/src/main/resources/
├── application.yml              ← defaults, wspólne dla wszystkich profili
├── application-dev.yml          ← profil 'dev' (lokalnie)
├── application-test.yml         ← profil 'test' (Testcontainers, CI)
└── application-prod.yml         ← profil 'prod' (produkcja)
```

Profil aktywowany przez `SPRING_PROFILES_ACTIVE` env var lub `-Dspring.profiles.active=dev`.

**Domyślny w MVP:** `dev`.

### Główne sekcje `application.yml`

```yaml
spring:
  application:
    name: multibot

  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/multibot}
    username: ${DB_USER:multibot}
    password: ${DB_PASSWORD:multibot}

  jpa:
    hibernate:
      ddl-auto: validate          # Flyway zarządza schematem
    properties:
      hibernate:
        format_sql: false

  flyway:
    enabled: true
    locations:
      - classpath:db/migration/common
      - classpath:db/migration/tenant
      - classpath:db/migration/document
      - classpath:db/migration/chat
      # ... per moduł (patrz database/schema.md)

  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

  modulith:
    republish-outstanding-events-on-restart: true

  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-3-5-haiku-latest
          temperature: 0.2
          max-tokens: 1024
    openai:
      api-key: ${OPENAI_API_KEY}
      embedding:
        options:
          model: text-embedding-3-small
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536

multibot:
  version: ${project.version}

  tenancy:
    mode: mock                    # mock | jwt | header

  file:
    storage:
      mode: local                 # local | s3
      local:
        base-path: ./storage

  chat:
    memory:
      window-size: 10             # ostatnich N wiadomości w prompcie

  rag:
    top-k: 5                      # liczba chunków do retrieve
    min-score: 0.7                # filtr similarity (cosine)
    system-prompt: |              # polski prompt (kopia z insurance-assistant)
      Jesteś asystentem...

  indexing:
    chunking:
      chunk-size: 500
      overlap: 50
    embedding:
      batch-size: 1               # post-MVP: zwiększyć dla wydajności
    retry:
      max-attempts: 3
      backoff-ms: 1000

  async:
    core-pool-size: 4
    max-pool-size: 10
    queue-capacity: 100

logging:
  level:
    root: INFO
    com.multibot: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%X{tenantId}] [%X{requestId}] %-5level %logger{36} - %msg%n"
```

### Konwencje dla custom properties

| Prefix | Co siedzi |
|--------|-----------|
| `multibot.tenancy.*` | konfiguracja modułu `tenancy/` |
| `multibot.file.*` | konfiguracja `file/` (storage mode, ścieżki) |
| `multibot.chat.*` | konfiguracja `chat/` (memory window) |
| `multibot.rag.*` | konfiguracja `rag/` (top-K, min-score, prompty) |
| `multibot.indexing.*` | konfiguracja `indexing/` (chunking, retry) |
| `multibot.async.*` | konfiguracja `config/AsyncConfig` |

**Reguła:** każdy moduł ma swoją sekcję pod `multibot.*`. Mapowane przez `@ConfigurationProperties` na rekordy konfiguracyjne wewnątrz modułu.

---

## 2. Profile

### `dev` (lokalnie)

- DataSource: localhost Postgres (docker-compose)
- Logi: plain text (czytelne w terminalu)
- Logging level `com.multibot`: `DEBUG`
- Swagger UI: **enabled** (`/swagger-ui.html`)
- CORS: permissive (`http://localhost:5173`)
- `multibot.tenancy.mode=mock`
- LLM: realne API (z `.env`)

### `test` (CI / unit + integration tests)

- DataSource: **Testcontainers** Postgres + pgvector (per test class lub per test method)
- Logi: minimum (tylko WARN/ERROR), bez SQL
- Logging level: `ROOT=WARN`, `com.multibot=INFO`
- Swagger: disabled
- `multibot.tenancy.mode=mock`
- LLM: **mock** (`MockChatClient`, `MockEmbeddingModel`) - nie hitujemy realnych API
- Flyway: standardowe migracje + dodatkowe `db/migration-test/` z seed data

### `prod` (post-MVP, produkcja)

- DataSource: zewnętrzny Postgres (Hetzner)
- Logi: **JSON** (strukturyzowane, do log aggregatora)
- Logging level: `ROOT=WARN`, `com.multibot=INFO`
- Swagger: **disabled** (security)
- CORS: tylko domena produkcyjna
- `multibot.tenancy.mode=jwt`
- Spring Security: **enabled**
- LLM: realne API z secrets manager / vault

---

## 3. Environment variables

### Secrets - NIE w `application.yml`

W repo trzymamy tylko **referencje** (`${ANTHROPIC_API_KEY}`). Wartości:
- Lokalnie: plik `.env` (NIE commitowany, w `.gitignore`)
- CI: GitHub Actions secrets / GitLab CI variables
- Produkcja: secrets manager / vault / docker secrets

### Lista wymaganych env vars

| Zmienna | Opis | Wymagane |
|---------|------|----------|
| `ANTHROPIC_API_KEY` | klucz do Anthropic API | ✅ tak |
| `OPENAI_API_KEY` | klucz do OpenAI API (embeddingi) | ✅ tak |
| `DB_URL` | JDBC URL Postgres | opcjonalne (defaults dla `dev`) |
| `DB_USER` | user Postgres | opcjonalne |
| `DB_PASSWORD` | hasło Postgres | opcjonalne |
| `SPRING_PROFILES_ACTIVE` | profil Spring | opcjonalne (default `dev`) |
| `JWT_SECRET` | sekret do podpisywania JWT | post-MVP |
| `S3_BUCKET` | bucket dla plików | post-MVP |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | dostęp do S3 | post-MVP |

### `.env` dla lokalnego dev

```
ANTHROPIC_API_KEY=sk-ant-...
OPENAI_API_KEY=sk-...
DB_URL=jdbc:postgresql://localhost:5432/multibot
DB_USER=multibot
DB_PASSWORD=multibot
SPRING_PROFILES_ACTIVE=dev
```

Plik ładowany przez IDE (IntelliJ "Spring Boot Run Configuration → Environment Variables → from file") lub `direnv` w terminalu.

**Plik NIE commitowany.** W repo tylko `.env.example` z placeholderami.

---

## 4. Obsługa błędów

### Hierarchia wyjątków (w `shared/exception/`)

```
DomainException (RuntimeException)
├── NotFoundException          → HTTP 404
├── ForbiddenException         → HTTP 403
├── ConflictException          → HTTP 409
└── ValidationException        → HTTP 400 (custom validation)
```

Plus standardowe Spring/Jakarta:
- `MethodArgumentNotValidException` (Jakarta Validation) → HTTP 400
- `HttpMessageNotReadableException` (zły JSON) → HTTP 400
- `MaxUploadSizeExceededException` → HTTP 413

### Globalny `@RestControllerAdvice`

Klasa `GlobalExceptionHandler` w `config/` (bo to przekrojowe):

| Wyjątek | HTTP | Co loguje | Co zwraca |
|---------|------|-----------|-----------|
| `NotFoundException` | 404 | WARN (z requestId + tenantId) | ProblemDetail |
| `ForbiddenException` | 403 | WARN | ProblemDetail (generic message - nie zdradzaj szczegółów) |
| `ConflictException` | 409 | WARN | ProblemDetail z polem `conflictingResource` |
| `ValidationException` | 400 | WARN | ProblemDetail z listą błędów |
| `MethodArgumentNotValidException` | 400 | DEBUG | ProblemDetail z field-level errors |
| Każdy inny `Exception` | 500 | **ERROR** + stack trace + alert | ProblemDetail (generic "Internal Server Error" - nie zdradzaj internals) |

### Format JSON błędu - RFC 7807 (ProblemDetail)

Spring 6 ma natywne wsparcie przez `org.springframework.http.ProblemDetail`. Używamy.

```json
{
  "type": "https://multibot.app/errors/document-not-found",
  "title": "Document Not Found",
  "status": 404,
  "detail": "Document with id 42 not found",
  "instance": "/api/documents/42",
  "code": "DOCUMENT_NOT_FOUND",
  "tenantId": 1,
  "traceId": "abc-123-def-456",
  "timestamp": "2025-05-22T08:15:30Z"
}
```

Pola:
- `type`, `title`, `status`, `detail`, `instance` - z RFC 7807
- `code` - nasz code do programatycznej obsługi (np. `DOCUMENT_NOT_FOUND`, `INDEXING_FAILED`)
- `tenantId`, `traceId` - dla debugowania (NIE pokazujemy w prod jeśli wrażliwe)
- `timestamp` - ISO-8601

Dla walidacji - dodatkowe pole `errors`:
```json
{
  ...
  "errors": [
    { "field": "filename", "message": "must not be blank" },
    { "field": "file", "message": "size exceeds 50MB" }
  ]
}
```

### Reguły

- **Nie zdradzaj internals klientowi.** 500 = "Internal Server Error", nie stack trace.
- **Loguj WSZYSTKO server-side.** Pełny stack trace + tenantId + requestId + payload (po sanityzacji).
- **Distinguish 404 vs 403** - dla bezpieczeństwa czasem lepiej zwracać 404 dla "istnieje ale nie twoje" (nie zdradzasz istnienia zasobu). W MVP używamy 404 dla user-facing endpointów.
- **Każdy error response = log entry.** Brak "silent failures".

---

## 5. Logging i obserwowalność

### Stack

- **SLF4J** + **Logback** (default Spring Boot)
- **MDC** (Mapped Diagnostic Context) - per-request kontekst w logach

### MDC fields

| Field | Skąd | Po co |
|-------|------|-------|
| `tenantId` | `TenantContextFilter` (set po `TenantContext.set`) | filtrowanie logów per tenant |
| `requestId` | `RequestIdFilter` (generuje UUID per request) lub z headera `X-Request-Id` | trace zapytania end-to-end |
| `userId` | post-MVP - z JWT po zalogowaniu | audyt |
| `endpoint` | path requestu | filtrowanie per endpoint |

MDC fields automatycznie pojawiają się w każdym logu wątku obsługującego request - bez ręcznego dodawania.

### Format logów

**Dev (plain text, czytelne):**
```
08:15:30.123 [tenant=1] [req=abc-123] INFO  c.m.document.DocumentService - Document uploaded: id=42, size=1024KB
```

**Prod (JSON, dla log aggregatora):**
```json
{
  "@timestamp": "2025-05-22T08:15:30.123Z",
  "level": "INFO",
  "logger": "com.multibot.document.DocumentService",
  "message": "Document uploaded: id=42, size=1024KB",
  "tenantId": 1,
  "requestId": "abc-123",
  "thread": "http-nio-8080-exec-3"
}
```

Switch przez `logback-spring.xml` z `<springProfile name="prod">`.

### Co logujemy zawsze

- Każdy request HTTP (przez `LoggingFilter` lub Spring Boot access log)
- Wszystkie błędy (5xx) z pełnym stack trace
- Wszystkie odrzucone walidacje (4xx) z detalem
- Każda publikacja eventu i konsumpcja (level DEBUG)
- LLM calls - prompt size, response size, tokens used, latency (level INFO)

### Czego NIE logujemy

- **Zawartość plików / dokumentów** - mogą zawierać PII klientów
- **Treść wiadomości chatu** - jw.
- **API keys, hasła, JWT tokens** - oczywiste
- **Pełny prompt z kontekstem** - może zawierać fragmenty dokumentów. Logujemy tylko **długość** promptu i **liczba tokenów**.

### Post-MVP - obserwowalność

| Tool | Po co | Status |
|------|-------|--------|
| Micrometer + Prometheus | metryki (request count, latency, error rate) | post-MVP |
| OpenTelemetry | distributed tracing | post-MVP |
| Sentry / Bugsnag | error tracking | post-MVP |
| Grafana | dashboards | post-MVP |
| Log aggregator (Loki / ELK) | centralizacja logów | post-MVP |

W MVP: stdout + odbiór ręczny w terminalu / `docker logs`.

---

## 6. Strategia testów

### Poziomy

| Poziom | Co testuje | Narzędzia | Szybkość |
|--------|------------|-----------|----------|
| **Unit** | jedna klasa w izolacji | JUnit 5 + Mockito + AssertJ | bardzo szybko (<10ms) |
| **Module integration** | jeden moduł, mock sąsiadów | Spring `@ApplicationModuleTest` + Mockito | szybko (~100ms-1s) |
| **Application integration** | całość, z bazą | `@SpringBootTest` + Testcontainers Postgres+pgvector | wolno (10s+) |
| **Event scenario** | flow asynchroniczny | Spring Modulith `Scenario` | średnio (1-5s) |

**Reguła:** piramida - dużo unit, mniej module, mało application. **MVP focus: unit + module + 1-2 happy path application.**

### Unit tests

Każda klasa application service (`DocumentService`, `IndexingService`, `RagService`) ma unit testy z mockami portów OUT.

Każdy mapper (DTO ↔ domain, domain ↔ JPA entity) ma test sprawdzający round-trip i edge cases.

Domain models z value objects - testy walidacji w konstruktorach (np. `TenantSlug` rzuca dla pustego stringa).

### Module integration tests

`@ApplicationModuleTest` izoluje moduł od reszty. Spring Modulith dostarcza:
- Boot tylko ten moduł + dependencies
- Mock sąsiednich modułów przez ich port IN (auto)
- Czyste tabele `event_publication`

Co testować na tym poziomie:
- Repository adapter mapuje domain ↔ JPA entity poprawnie
- Service emituje event po `save()`
- Service walidacja biznesowa (z mockowanym repo)

### Application integration tests

Tylko **happy paths** dla głównych flow'ów:
- Upload dokumentu → indeksacja → wszystkie chunki w pgvector
- Zadanie pytania → RAG → odpowiedź zawiera cytat

Testcontainers Postgres+pgvector:
```
@Testcontainers
@SpringBootTest
class DocumentUploadIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
        .withDatabaseName("multibot_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void registerDb(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ... test methods
}
```

### `@WithTenant` - helper dla testów

Adnotacja na metodzie testu ustawia `TenantContext` przed testem i czyści po:

```
@Test
@WithTenant(1L)
void test_uploadDocument_savesWithCorrectTenantId() {
    // TenantContext.get() == TenantId(1L) automatycznie
    ...
}
```

Bez tej adnotacji - `TenantContext.get()` rzuci `IllegalStateException` (bo nie ustawiony poza requestem).

### Mock LLM w testach

`@TestConfiguration` w `test`:

```
@TestConfiguration
class MockAiConfig {
    @Bean @Primary
    ChatClient mockChatClient() {
        return new MockChatClient("Pełna mock odpowiedź...");
    }

    @Bean @Primary
    EmbeddingModel mockEmbeddingModel() {
        return text -> new float[1536];  // dummy vector
    }
}
```

**Nigdy nie hitujemy realnych OpenAI/Anthropic w testach** - koszty, niedeterminizm, rate limity.

Wyjątek: jeden ręcznie odpalany test E2E z realnym LLM (taggowany `@Tag("manual")`) - do walidacji jakości RAG-a co pewien czas. NIE w CI.

### Event scenario tests

Patrz [`events.md`](events.md) sekcja 8.

### Coverage gate

**Brak w MVP.** Focus na działającym MVP, nie na metrykach. Po-MVP może być sensowne dorzucenie JaCoCo + minimum 60% line coverage.

---

## 7. Build i lokalne uruchomienie

### Maven structure

`backend/pom.xml` - reużywamy z `insurance-assistant`, dodajemy:
- `spring-modulith-starter-core`
- `spring-modulith-starter-jpa`
- `spring-modulith-events-api`
- `spring-modulith-starter-test`
- `flyway-core`, `flyway-database-postgresql`
- `org.apache.tika:tika-parsers-standard-package`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- `testcontainers`, `testcontainers-postgres`

### Lokalne uruchomienie

```bash
# 1. Postgres + pgvector w docker-compose
cd multibot/
docker-compose up -d

# 2. Skopiuj .env.example do .env i wpisz klucze API
cp .env.example .env
# edytuj .env: ANTHROPIC_API_KEY, OPENAI_API_KEY

# 3. Backend
cd backend/
./mvnw spring-boot:run

# Aplikacja na http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

### Hot reload

- IntelliJ: "Run Configuration" z `spring-boot-devtools` na classpath - auto-restart na zmianę kodu
- Frontend: osobny `npm run dev` (Vite HMR, nie dotyczy backendu)

### Smoke check

Po uruchomieniu:
```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}

curl http://localhost:8080/api/documents
# [] (pusta lista, ale 200 OK = tenant_id=1 hardcoded działa)
```

---

## 8. Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Strategia ogólna backendu | [`overview.md`](overview.md) |
| Wzorzec hexagonalny | [`hexagonal-pattern.md`](hexagonal-pattern.md) |
| Moduły infrastrukturalne (config jest tam opisany) | [`modules-infrastructure.md`](modules-infrastructure.md) |
| Moduły biznesowe | [`modules-business.md`](modules-business.md) |
| Spring Modulith + eventy | [`events.md`](events.md) |
| Schemat bazy + migracje Flyway | [`../../database/schema.md`](../../database/schema.md) |
| Strategia multi-tenancy | [`../multi-tenancy.md`](../multi-tenancy.md) |
