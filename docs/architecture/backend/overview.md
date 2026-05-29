# Backend - widok ogólny

> **Aktualizacja (Fazy A–D + pivot):** doszły moduły `organization`, `auth`, `billing`, `security`,
> `superadmin`, `bots`, `widget`, `botversion`. Aktualna lista modułów + ich rola →
> [`modules-business.md`](modules-business.md); model Organizacja→Boty → [`../multi-tenancy.md`](../multi-tenancy.md).

Filozofia organizacji kodu backendu, drzewo pakietów, kluczowe decyzje techniczne.

Szczegóły:
- Wzorzec hexagonalny wewnątrz modułu → [`hexagonal-pattern.md`](hexagonal-pattern.md)
- Moduły infrastrukturalne → [`modules-infrastructure.md`](modules-infrastructure.md)
- Moduły biznesowe → [`modules-business.md`](modules-business.md)
- Spring Modulith + Events → [`events.md`](events.md)
- Konfiguracja + obsługa błędów + testy → [`operations.md`](operations.md)

---

## 1. Strategia organizacji - "microservice-ready modulith"

### Filozofia

Aplikacja jest monolitem (jeden proces, jeden deploy), ale wewnętrznie zorganizowanym tak, **żeby każdy moduł mógł być wyciągnięty do osobnego serwisu bez przepisywania reszty kodu**.

Każdy moduł to "mini-aplikacja" z własnym:
- modelem domenowym (czystym, bez frameworków)
- portami (interfejsami)
- adapterami (implementacjami: DB, integracje zewnętrzne)
- (opcjonalnie) REST API

Moduły komunikują się **wyłącznie przez publiczne porty IN** drugiego modułu. Nigdy bezpośrednio przez wewnętrzne klasy. To enforce'uje Spring Modulith.

### Trzy zasady

1. **Hexagonal per module** - każdy moduł ma swoje `domain/`, `application/`, `infrastructure/`, opcjonalnie `api/`. Reguły zależności są ścisłe (patrz [`hexagonal-pattern.md`](hexagonal-pattern.md)).

2. **Czysta domena** - modele domenowe (`Document`, `ChatSession`, `Chunk`) to czyste POJO/recordy bez `@Entity`, bez Spring, bez Hibernate. Oddzielne JPA entities żyją wyłącznie w `infrastructure/persistence/`. Mappery konwertują w obie strony.

3. **Komunikacja między modułami przez porty IN lub eventy** - moduł A nie wie nic o wewnętrznej strukturze modułu B. Wie tylko o jego publicznych use case'ach (`module.b.domain.port.in.*`) lub eventach (`module.b.domain.event.*`).

### Co to nam daje

- **Łatwa extrakcja do mikroserwisu** - dziś moduł `file/` używa `LocalFileStorageAdapter`. Jutro wyrzucamy go do osobnego serwisu i zamieniamy adapter na `FileServiceHttpClient`. Reszta kodu się nie zmienia.
- **Testowalność w izolacji** - mockuje się sąsiednie moduły przez ich porty IN.
- **Jasne ownership** - bug w indeksacji = problem w `indexing/`. Bez szukania po pięciu pakietach.
- **Spring Modulith enforce'uje granice** - test w buildzie failuje jeśli ktoś naruszył.

### Co to kosztuje

- **Więcej kodu** - dwie reprezentacje encji (domain + JPA), mappery, port interfaces. ~2-3x więcej klas niż klasyczny Spring MVC.
- **Brak ACID między modułami** - moduł A robi transakcję u siebie, woła moduł B, B robi swoją. Jeśli B zawiedzie po A - eventual consistency, sagi, outbox pattern.
- **Próg wejścia** - junior dev musi zrozumieć dlaczego nie może po prostu zawołać `documentJpaRepository` z chatu.

Akceptujemy ten koszt, bo platforma ma rosnąć - tańsze projektować dobrze od początku niż refactorować potem.

---

## 2. Top-level package tree

```
com.multibot/
├── MultibotApplication.java
│
│  ────────── shared kernel ──────────
├── shared/                    # value objects, base exceptions, marker interfaces
│
│  ────────── cross-cutting infrastructure ──────────
├── tenancy/                   # TenantContext + Filter + Resolvers
├── config/                    # Web/Async/OpenAPI/Jackson @Configuration
├── ai/                        # Spring AI beans (ChatClient, VectorStore, EmbeddingModel)
│
│  ────────── business modules (REST API) ──────────
├── tenant/                    # zarządzanie tenantami
├── user/                      # (post-MVP) userzy panelu admina
├── document/                  # upload, list, delete dokumentów
├── chat/                      # sesje + wiadomości + SSE streaming
│
│  ────────── orchestrator modules (bez API) ──────────
├── file/                      # storage plików (local / S3) - wystawia porty IN
├── vector/                    # pgvector - wymusza filtr tenant_id
├── indexing/                  # async pipeline: Tika → chunk → embed
└── rag/                       # retrieval + prompt + LLM call
```

### Co siedzi w każdej grupie

| Grupa | Charakterystyka | Lista modułów |
|-------|-----------------|---------------|
| **shared kernel** | Bardzo małe, value objects, marker interfaces. Może być importowany przez wszystkich bez ograniczeń | `shared` |
| **cross-cutting infrastructure** | Techniczne wsparcie używane przez wiele modułów. Nie ma biznesu, nie ma własnej domeny | `tenancy`, `config`, `ai` |
| **business modules** | Mają domenę biznesową, wystawiają REST API dla świata zewnętrznego | `tenant`, `user`, `document`, `chat` |
| **orchestrator modules** | Mają domenę, ale brak REST API - są wołane przez business modules lub przez eventy | `file`, `vector`, `indexing`, `rag` |

**Szczegółowy opis każdego modułu** → [`modules-infrastructure.md`](modules-infrastructure.md) + [`modules-business.md`](modules-business.md).

---

## 3. Graf zależności modułów

**Diagram:** [`../../diagrams/backend-modules.puml`](../../diagrams/backend-modules.puml)

Pokazuje kierunkowe zależności między modułami: kto kogo wywołuje, jakie eventy publikuje, kto słucha.

Najważniejsze fakty z diagramu:
- `shared`, `tenancy`, `config` → używane przez wszystkich (linie pominięte dla czytelności)
- `document` wywołuje `file` (zapis pliku)
- `document` publikuje event `DocumentUploaded` → słucha `indexing`
- `indexing` używa `file` (czyta plik), `ai` (embeddingi), `vector` (zapisuje chunki)
- `indexing` publikuje event `IndexingCompleted` → słucha `document` (update statusu)
- `chat` wywołuje `rag`
- `rag` używa `vector` (search) i `ai` (LLM)

**Zasada cykli:** brak. Modulith failuje jeśli powstaje cykl A → B → A.

---

## 4. Decyzje techniczne

Krótko, z uzasadnieniem. Szczegóły implementacyjne w innych plikach.

### Język i framework
- **Java 21** - LTS, records, pattern matching, virtual threads (do rozważenia dla SSE)
- **Spring Boot 3.x** - aktualna główna wersja, JDK 17+, Jakarta EE namespace
- **Spring Modulith** - enforce granice modułów + Events z transactional outbox

### Struktura kodu
- **Hexagonal per module** - domain/application/infrastructure/api (szczegóły: [`hexagonal-pattern.md`](hexagonal-pattern.md))
- **Domena ≠ JPA entity** - dwie osobne klasy, mapper w `infrastructure/persistence/`
- **Domain to czyste POJO / Java records** - bez Lomboka, bez Spring, bez Hibernate
- **JPA entities z Lombokiem** (`@Getter`, `@Setter`, `@NoArgsConstructor`) - bo Hibernate wymaga setterów i no-arg constructor

### DTO i mapping
- **DTOs jako Java records** (`record DocumentResponse(...)`) - immutable, krótkie
- **Mapping ręczny** - statyczne metody na DTO (`DocumentResponse.from(Document)`) i na encji JPA (`DocumentJpaEntity.toDomain()`)
- **Bez MapStruct** w MVP - dodatkowa dependency, generator kodu, mało zysku przy małych modelach
- Można dorzucić MapStruct później jeśli mappery zaczną się powtarzać

### Walidacja
- **Jakarta Validation** (`@NotNull`, `@NotBlank`, `@Size`, `@Email`) w DTO requestów
- `@Valid` w kontrolerach
- Globalny handler błędów walidacji → format JSON `{ "errors": [{"field": "...", "message": "..."}] }`

### Persistence
- **Spring Data JPA + Hibernate 6** - na JPA entities w `infrastructure/persistence/`
- **`@TenantId`** na każdej JPA entity - automatyczne filtrowanie (patrz [multi-tenancy.md](../multi-tenancy.md))
- **Flyway** - migracje per moduł (`db/migration/{module}/V1__...sql`)
- **PostgreSQL 16 + pgvector** - jedna baza na dane relacyjne i wektory
- **Bez Hibernate Envers / audytowania** w MVP

### REST API
- **`/api/...`** bez wersjonowania w MVP. Wersja w breaking changes (`/api/v2/...`) później.
- **SpringDoc OpenAPI** - auto-generated swagger UI pod `/swagger-ui.html`
- **Globalny `@ControllerAdvice`** + hierarchia wyjątków (`NotFoundException`, `ForbiddenException`, `ValidationException`) → szczegóły w [`operations.md`](operations.md)
- **Streaming chatu: SSE** (Server-Sent Events) - prościej niż WebSocket, wystarczy dla one-way streamingu

### Async i events
- **Spring Modulith Events** zamiast bezpośredniego `@Async` - transactional outbox, persistent log eventów, retry, observability
- **TaskExecutor z propagacją TenantContext** - dla `@Async` (kiedy używany ręcznie poza Modulith Events)
- Szczegóły → [`events.md`](events.md)

### Security
- **Brak Spring Security w MVP** - tenant_id hardcoded na 1, bez login/register
- Post-MVP: Spring Security + JWT + Spring OAuth2 Resource Server

### Logging i obserwowalność
- **SLF4J + Logback** - standard Spring
- **MDC** (Mapped Diagnostic Context) - `tenant_id`, `request_id`, `user_id` w każdym logu
- **Strukturyzowane logi (JSON)** - włączane przez profil `prod`, plain text w `dev`
- **Bez Prometheus / OpenTelemetry** w MVP - dorzucamy razem z deployment produkcyjnym

### Testy
- **JUnit 5** + **AssertJ** + **Mockito**
- **Testcontainers** dla Postgres + pgvector (testy integracyjne)
- **`@WithTenant(1L)`** - testowa adnotacja ustawiająca TenantContext
- **Mock LLM** w testach (nie hitujemy realnych API w CI) - przez interfejs Spring AI
- Szczegóły → [`operations.md`](operations.md)

### Build i tooling
- **Maven** (jak insurance-assistant - reużywamy `pom.xml`)
- **Java code style** - Google Java Style albo intelliJ default, do potwierdzenia
- **Brak code coverage gate** w MVP - skupiamy się na działającym MVP, nie na metrykach

---

## 5. Co reużujemy z `insurance-assistant`

Refresh z [`../../brief.md`](../../brief.md) - co zabieramy 1:1, co przerabiamy.

| Z insurance-assistant | Trafia do | Jak |
|-----------------------|-----------|-----|
| `pom.xml` (deps Spring AI, pgvector, JDBC) | `multibot/multibot-backend/pom.xml` | 1:1 + dodać Spring Modulith, Spring Web, Flyway, Tika |
| `docker-compose.yml` (Postgres + pgvector) | `multibot/docker-compose.yml` | 1:1 |
| `application.properties` (Spring AI config, Claude + OpenAI) | `multibot/multibot-backend/src/main/resources/application.yml` | konwersja .properties → .yml |
| `RagConfig` chunking + seed | `ai/` jako referencja, ale rozbity: chunking → `indexing/application/ChunkingPolicy`, seed → odrzucamy (mamy realny upload) |
| `ChatConfig` ChatClient + Memory | `ai/SpringAiConfig.java` | bez zmian semantycznych |
| `QuestionAnswerAdvisor` pattern | `rag/application/RagService.java` | przepisać w nowej strukturze |
| Polskie system prompty | `rag/application/PromptBuilder.java` | 1:1 |
| Hardcoded `OWU.md` | wyrzucamy | mamy realny upload |
| `InsuranceTools` (tool calling) | odkładamy do post-MVP | nie trafia do MVP w ogóle |
| `Classification` (structured output) | odkładamy do post-MVP | jw. |

**Strategia:** najpierw budujemy szkielet multibota od zera (Maven init, struktura pakietów, Spring Modulith), potem **świadomie przenosimy** wybrane pliki z insurance-assistant dostosowując je. Nie kopiujemy folderu i nie zaczynamy refactorować.

---

## 6. Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Jak wygląda struktura wewnątrz pojedynczego modułu | [`hexagonal-pattern.md`](hexagonal-pattern.md) |
| Co robi konkretnie `tenancy/`, `config/`, `ai/`, `shared/` | [`modules-infrastructure.md`](modules-infrastructure.md) |
| Co robi konkretnie `document/`, `chat/`, `rag/`, `file/`, `vector/`, `indexing/` | [`modules-business.md`](modules-business.md) |
| Jak działają eventy między modułami | [`events.md`](events.md) |
| Konfiguracja Spring (application.yml, profile), obsługa błędów, testy | [`operations.md`](operations.md) |
| Strategia multi-tenancy (TenantContext, @TenantId, vector filtering) | [`../multi-tenancy.md`](../multi-tenancy.md) |
| Wysokopoziomowy obraz całego systemu | [`../overview.md`](../overview.md) |
