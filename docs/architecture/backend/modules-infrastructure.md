# Moduły infrastrukturalne

Moduły **przekrojowe** - bez biznesu, używane przez wszystkich. Cztery sztuki:

| Moduł | Cel | Czy hexagonal |
|-------|-----|---------------|
| [`shared/`](#shared-shared-kernel) | Shared kernel: value objects, marker interfaces, bazowe wyjątki | NIE - tylko klasy |
| [`tenancy/`](#tenancy-multi-tenancy-infrastructure) | TenantContext + Filter + Resolvers + Hibernate plumbing | częściowo (static facade) |
| [`config/`](#config-spring-konfiguracja-przekrojowa) | Spring `@Configuration`: Web, Async, OpenAPI, Jackson | NIE - tylko beany |
| [`ai/`](#ai-spring-ai-beany) | Beany Spring AI: `ChatClient`, `EmbeddingModel` | NIE - tylko beany |

Wszystkie poniższe są **importowalne przez wszystkich** - mogą być wstrzykiwane gdziekolwiek bez naruszania granic modułów (Spring Modulith dostaje to do listy `allowedDependencies`).

---

## `shared/` - shared kernel

### Cel

Małe, fundamentalne klasy używane przez **każdy** moduł. Bardzo wąski zakres - tylko to, co naprawdę musi być wspólne.

**Reguła:** jak coś przestaje być używane przez 2+ moduły - przenosimy z `shared/` do modułu, w którym żyje. `shared/` ma pozostać małe.

### Struktura

```
shared/
├── tenant/
│   ├── TenantId.java                ← value object (record z Long)
│   └── TenantOwned.java             ← marker interface
│
├── exception/
│   ├── DomainException.java         ← bazowy wyjątek domenowy
│   ├── NotFoundException.java       ← 404
│   ├── ForbiddenException.java      ← 403
│   ├── ValidationException.java     ← 400 (poza Jakarta Validation)
│   └── ConflictException.java       ← 409
│
└── pagination/                      ← opcjonalnie, jeśli pojawi się potrzeba
    ├── PageRequest.java             ← record(int page, int size, String sort)
    └── PagedResult.java             ← record(List<T> items, int totalPages, ...)
```

### Kluczowe klasy

**`TenantId`** - record opakowujący `Long`. Po to, żeby `Long tenantId` nie był zwykłym longiem - sygnatura `Document save(TenantId tenant, ...)` jest dużo bezpieczniejsza niż `save(Long, ...)`.

**`TenantOwned`** - interfejs `getTenantId(): TenantId`. Implementowany przez każdy agregat w domain layer modułów (`Document`, `ChatSession`, `Message`). Używany w `TenantSecurityCheck.assertSameTenant(...)` (patrz [`../multi-tenancy.md`](../multi-tenancy.md)).

**Wyjątki bazowe** - hierarchia dla globalnego `@ControllerAdvice`:
- `DomainException` (root) → mapowany na 500 + alert
- `NotFoundException extends DomainException` → 404
- `ForbiddenException extends DomainException` → 403
- `ValidationException extends DomainException` → 400 (dla custom walidacji)
- `ConflictException extends DomainException` → 409 (np. duplicate filename)

Szczegóły mapowania → [`operations.md`](operations.md).

### Co tu NIE należy

- **Wspólne DTO** - DTOs są lokalne per moduł (`api/dto/`)
- **Spring beany** - shared to czyste klasy, nie ma `@Component`
- **Utility classes typu `Strings.isBlank()`** - to overkill, użyj Apache Commons / Guava jeśli już
- **Common entity z `@Entity`** - bazowe encje JPA są w `shared/` tylko jeśli są POJO bez JPA. Z JPA → osobny `BaseJpaEntity` w `infrastructure` modułów

---

## `tenancy/` - multi-tenancy infrastructure

### Cel

Realizacja strategii multi-tenancy opisanej w [`../multi-tenancy.md`](../multi-tenancy.md). Jeden moduł trzyma wszystkie klasy odpowiedzialne za:
- ustawianie i odczyt aktualnego tenant_id (TenantContext)
- ustawianie kontekstu na początku każdego requestu (Filter + Resolvers)
- integracja z Hibernate (CurrentTenantIdentifierResolver)
- propagacja kontekstu do wątków asynchronicznych

### Struktura

Specyficzna - nie pełen hexagon, bo publiczne API to **statyczna fasada**, nie use case'y.

```
tenancy/
├── TenantContext.java                            ← public static facade (jedyne publiczne API)
│
├── infrastructure/
│   ├── ThreadLocalTenantContextHolder.java       ← package-private, wewnętrzny ThreadLocal
│   │
│   ├── filter/
│   │   └── TenantContextFilter.java              ← Spring OncePerRequestFilter
│   │
│   ├── resolver/
│   │   ├── TenantResolver.java                   ← interface
│   │   ├── MockTenantResolver.java               ← MVP: zawsze 1L
│   │   ├── JwtTenantResolver.java                ← post-MVP: z JWT
│   │   └── HeaderTenantResolver.java             ← post-MVP: z X-Tenant-Id dla widgetu
│   │
│   ├── hibernate/
│   │   └── TenantIdResolver.java                 ← implements CurrentTenantIdentifierResolver
│   │
│   ├── async/
│   │   └── TenantAwareTaskDecorator.java         ← propagacja TenantContext do @Async
│   │
│   └── test/
│       ├── WithTenant.java                       ← @WithTenant(1L) adnotacja
│       └── WithTenantExtension.java              ← JUnit 5 Extension
│
└── package-info.java                             ← @ApplicationModule(displayName = "Tenancy")
```

### Kluczowe klasy

**`TenantContext`** - publiczne statyczne API. Trzy metody:
- `static TenantId get()` - rzuca `IllegalStateException` jeśli nie ustawiony
- `static Optional<TenantId> tryGet()` - bez wyjątku, zwraca `Optional`
- `static void set(TenantId)`, `static void clear()` - ustawia i czyści (używane przez Filter)

Static facade ukrywa implementację (ThreadLocal). Można wymienić wewnętrznie (np. na coś typu Scope w Reactor / virtual threads context) bez zmiany API.

**`TenantContextFilter`** - `OncePerRequestFilter` w Spring. Lifecycle:
1. Filter pyta wstrzykniętego `TenantResolver` o tenant dla tego requestu
2. `TenantContext.set(tenantId)`
3. `chain.doFilter(request, response)` - cały request leci z ustawionym kontekstem
4. W `finally`: `TenantContext.clear()` - **krytyczne** ze względu na thread pool

Filter jest tylko jeden. Implementacja resolvera zmienia się przez `@ConditionalOnProperty`.

**`TenantResolver`** - interfejs `TenantId resolve(HttpServletRequest request)`. Trzy implementacje:
- `MockTenantResolver` - aktywny dla `multibot.tenancy.mode=mock` (MVP). Zwraca stałe `TenantId(1L)`.
- `JwtTenantResolver` - aktywny dla `multibot.tenancy.mode=jwt`. Parsuje JWT z `Authorization: Bearer ...`, wyciąga claim `tenant_id`.
- `HeaderTenantResolver` - aktywny dla `multibot.tenancy.mode=header`. Czyta `X-Tenant-Id` (dla widgetu w przyszłości, walidowane przez widget key).

**`TenantIdResolver`** - implementacja `CurrentTenantIdentifierResolver<Long>` z Hibernate. Spring znajduje go po typie. Hibernate woła `resolveCurrentTenantIdentifier()` przed każdym query → zwracamy `TenantContext.get().value()`. To uruchamia magię `@TenantId`.

**`TenantAwareTaskDecorator`** - dekorator `TaskDecorator` ze Spring. Kiedy `@Async` przekazuje zadanie do thread poola, decorator:
1. Czyta `TenantContext.get()` w wątku oryginalnym
2. Zapisuje
3. W wątku workera ustawia ten sam tenant przed wykonaniem
4. Czyści po wykonaniu

Bez tego `@Async` job nie wiedziałby kto go zlecił - leciał by z pustym kontekstem. (W praktyce dla MVP używamy Spring Modulith Events które same dbają o kontekst - patrz [`events.md`](events.md).)

**`@WithTenant`** + `WithTenantExtension` - JUnit 5 helper do testów. Adnotacja na klasie testowej lub metodzie ustawia TenantContext przed testem i czyści po.

### Zależności

- **Kto używa:** wszyscy (przez `TenantContext.get()`)
- **Spring auto-config:** `TenantContextFilter` rejestrowany jako bean, `TenantResolver` wstrzykiwany warunkowo (`@ConditionalOnProperty`)
- **Konfiguracja:** property `multibot.tenancy.mode` (`mock` | `jwt` | `header`)

### Stan w MVP

- `multibot.tenancy.mode=mock` w `application.yml`
- Aktywny: `MockTenantResolver`
- Tabela `tenants` w bazie ma jeden seed record z id=1, name="Default"
- Zero kodu związane z JWT/Spring Security

---

## `config/` - Spring konfiguracja przekrojowa

### Cel

Beany Spring **niespecyficzne dla żadnego konkretnego modułu**:
- Web (CORS, message converters)
- Async (`TaskExecutor` z `TenantAwareTaskDecorator`)
- OpenAPI (SpringDoc)
- Jackson (ObjectMapper)

To NIE jest moduł hexagonalny - to po prostu zbiór `@Configuration` klas.

### Struktura

```
config/
├── WebConfig.java          ← CORS, message converters
├── AsyncConfig.java        ← @EnableAsync, custom TaskExecutor z tenant propagation
├── OpenApiConfig.java      ← SpringDoc: tytuł, wersja, security schemes
├── JacksonConfig.java      ← ObjectMapper: Instant, BigDecimal, nulls
└── package-info.java       ← @ApplicationModule(displayName = "Config")
```

### Kluczowe klasy

**`WebConfig`** - `WebMvcConfigurer`:
- CORS dla origin'ów Frontend Panela (`http://localhost:5173` w MVP)
- Static resources (jeśli serwujemy frontend z BE - w MVP NIE, frontend osobno)

**`AsyncConfig`** - `@EnableAsync` + `@Bean TaskExecutor`:
- `ThreadPoolTaskExecutor` z rozsądnymi parametrami (core=4, max=10, queue=100 dla MVP)
- `TaskDecorator` = `TenantAwareTaskDecorator` z `tenancy/`
- Wszystkie `@Async` w aplikacji używają tego executora

**`OpenApiConfig`** - `@Bean OpenAPI`:
- Tytuł "Multibot API"
- Wersja z `application.yml` (`@Value("${multibot.version}")`)
- Security scheme (post-MVP: JWT Bearer)
- Path `/swagger-ui.html` i `/v3/api-docs` aktywne w `dev`, wyłączone w `prod`

**`JacksonConfig`** - `Jackson2ObjectMapperBuilderCustomizer`:
- `Instant` serializacja ISO-8601 (`2025-05-22T08:00:00Z`)
- `BigDecimal` jako string (nie float - precision)
- `JsonInclude.Include.NON_NULL` (puste pola pomijane w response)
- `JavaTimeModule` (handling Java 8+ time)

### Zależności

- **Kto używa:** wszyscy (transparentne - Spring auto-config)
- **Profile:** część konfiguracji per profil (np. CORS w `dev` permissive, w `prod` strict)

### Stan w MVP

Wszystkie 4 klasy gotowe od początku - to fundament aplikacji.

---

## `ai/` - Spring AI beany

### Cel

Konfiguracja Spring AI - **tylko beany**, bez logiki biznesowej, bez hexagonu.

`ai/` udostępnia `ChatClient` i `EmbeddingModel` jako Spring beany. Inne moduły (`rag/`, `indexing/`) wstrzykują je bezpośrednio przez Spring DI - **nie owijamy ich w porty hexagonalne**.

Decyzja świadoma: Spring AI samo w sobie jest abstrakcją nad LLM providerami (możemy zmienić Anthropic na OpenAI przez property). Dodawanie własnego portu wokół tego = niepotrzebna warstwa.

**Vector store** (`pgvector` jako Spring AI VectorStore) NIE jest w `ai/` - siedzi w module `vector/` (bo wymaga wrappingu dla multi-tenancy).

### Struktura

```
ai/
├── SpringAiConfig.java         ← @Configuration: ChatClient, EmbeddingModel, TokenTextSplitter
├── PromptDefaults.java         ← (opcjonalnie) wspólne defaults dla promptów - temperature, max tokens
└── package-info.java           ← @ApplicationModule(displayName = "AI")
```

### Kluczowe klasy

**`SpringAiConfig`** - `@Configuration`:

- **`@Bean ChatClient`** - klient do LLM. W MVP wstępnie Anthropic Claude (Haiku), z możliwością przełączenia na OpenAI przez property `multibot.ai.llm.provider=anthropic|openai`. Spring AI ma auto-config dla obu - my tylko wybieramy.

- **`@Bean EmbeddingModel`** - model do embeddingów. OpenAI `text-embedding-3-small` (1536 wymiarów). Konfiguracja przez `application.yml`.

- **`@Bean TokenTextSplitter`** - chunker tekstu. `chunkSize=500, overlap=50` (do tuningu). Używany przez `indexing/`.

- **Chat memory** - dla MVP używamy `MessageWindowChatMemory` z `chat/`-managementem historii (a nie domyślne in-memory Spring AI). Patrz `chat/` w [`modules-business.md`](modules-business.md).

**`PromptDefaults`** (opcjonalnie) - stałe / record z domyślnymi parametrami:
- `temperature` (np. 0.2 dla RAG)
- `maxTokens` (np. 1024)
- `topP`

Aby uniknąć rozjazdu między modułami, ale można też trzymać te wartości w `application.yml` i wstrzykiwać przez `@Value`.

### Zależności

- **Kto używa:** `indexing/` (EmbeddingModel + TokenTextSplitter), `rag/` (ChatClient + EmbeddingModel), `vector/` (NIE - vector/ używa Spring AI VectorStore własnym setupem)
- **Klucze API:** czytane z env vars (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`) → patrz [`operations.md`](operations.md)

### Stan w MVP

- LLM: Anthropic Claude (Haiku) - tańszy, szybszy dla MVP
- Embedding: OpenAI `text-embedding-3-small` - dobry tani standard
- Token splitter: 500 tokens chunks, 50 overlap (do walidacji jakości RAG po uruchomieniu)
- Reużywamy konfigurację z `insurance-assistant` (`ChatConfig`, `RagConfig`) - przepisując pod nową strukturę

---

## Spring Modulith - co eksportują infrastrukturalne moduły

Wszystkie cztery są oznaczone jako "open" w Spring Modulith - czyli **inne moduły mogą importować z nich cokolwiek** (nie muszą wskazywać konkretnych subpaczek w `allowedDependencies`).

Dlaczego: te moduły z definicji są "shared infrastructure". Próba pilnowania ich subpaczek byłaby przesadą.

W `package-info.java` modułów biznesowych po prostu wymieniamy:
```
allowedDependencies = { "shared", "tenancy", "ai", ... }
```

Bez `::subpackage` - to oznacza "cały moduł dostępny".

---

## Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Moduły biznesowe (tenant, document, chat, rag itp.) | [`modules-business.md`](modules-business.md) |
| Spring Modulith setup + jak działają eventy | [`events.md`](events.md) |
| application.yml, profile, env vars | [`operations.md`](operations.md) |
| Strategia multi-tenancy (głębokie szczegóły) | [`../multi-tenancy.md`](../multi-tenancy.md) |
| Wzorzec hexagonalny per moduł | [`hexagonal-pattern.md`](hexagonal-pattern.md) |
