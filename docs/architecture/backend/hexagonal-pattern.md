# Wzorzec hexagonalny w pojedynczym module

Opis jak wygląda **struktura wewnętrzna** każdego modułu (`document/`, `chat/`, `file/` itd.).

To jest "instrukcja obsługi" - przy tworzeniu nowego modułu trzymamy się tego wzorca.

---

## 1. Wzorzec w pigułce

Każdy moduł ma 4 warstwy:

| Warstwa | Pakiet | Po co |
|---------|--------|-------|
| **domain** | `<moduł>.domain.*` | Czysta domena: modele biznesowe + interfejsy portów. Zero frameworków. |
| **application** | `<moduł>.application.*` | Implementacja use case'ów - orkiestracja, transakcje, logika biznesowa. |
| **infrastructure** | `<moduł>.infrastructure.*` | Adaptery wyjściowe: persistence, zewnętrzne API, kolejki. Cała "techniczna" strona. |
| **api** | `<moduł>.api.*` | Adapter wejściowy: REST controllery, DTO, mappery API. Tylko jeśli moduł wystawia REST. |

**Reguła:** zależności idą **do środka** (`api`/`infrastructure` → `application` → `domain`). Domena nie wie o niczym.

**Diagram:** [`../../diagrams/backend-hexagonal-module.puml`](../../diagrams/backend-hexagonal-module.puml)

---

## 2. Reguły zależności (najważniejsza część)

| Warstwa | Może importować z... | NIE może importować z... |
|---------|----------------------|--------------------------|
| `domain` | TYLKO `shared/`, JDK | nic innego (zero frameworków, zero `infrastructure`, zero `application`) |
| `application` | `domain` + `shared` + Spring (`@Service`, `@Transactional`) | `infrastructure`, `api` |
| `infrastructure` | `domain` (implementuje porty OUT) + `shared` + frameworki (JPA, Spring AI, AWS SDK...) | `api`, `application` |
| `api` | `domain.port.in` + `domain.model` + `shared` + Spring Web | `application`, `infrastructure` |

**Kluczowe konsekwencje:**

1. **`application` NIE może importować JPA entity** - to byłoby łamanie hexagonu. Zamiast tego importuje `DocumentRepository` (port OUT) i operuje na `Document` (domain model).

2. **`api` NIE może importować `DocumentService`** - kontroler woła use case przez port IN (`UploadDocumentUseCase`). To że za interfejsem siedzi `DocumentService` to detal aplikacji.

3. **`infrastructure` NIE może wołać `api`** - jedna strona. REST → use case → adapter, nigdy w drugą.

4. **Inny moduł** (np. `document` wołający `file`) importuje **TYLKO** publiczne porty IN sąsiada (`com.multibot.file.domain.port.in.*`) + jego domain model (`com.multibot.file.domain.model.*`).

Spring Modulith wymusza te zasady w teście (patrz [`events.md`](events.md)).

---

## 3. Struktura katalogów wewnątrz modułu

Pełny szablon (na przykładzie hipotetycznego modułu `document/`):

```
document/
├── package-info.java                     ← deklaracja modułu dla Spring Modulith
│
├── domain/
│   ├── model/
│   │   ├── Document.java                 ← agregat / model domenowy (record / POJO)
│   │   ├── DocumentId.java               ← value object (typ ID)
│   │   ├── DocumentStatus.java           ← enum
│   │   └── DocumentMetadata.java         ← value object
│   │
│   ├── port/
│   │   ├── in/                           ← use case interfaces (publiczne)
│   │   │   ├── UploadDocumentUseCase.java
│   │   │   ├── DeleteDocumentUseCase.java
│   │   │   ├── ListDocumentsUseCase.java
│   │   │   └── GetDocumentStatusUseCase.java
│   │   │
│   │   └── out/                          ← interfejsy które domena potrzebuje
│   │       └── DocumentRepository.java
│   │
│   ├── event/                            ← eventy publikowane przez moduł
│   │   ├── DocumentUploadedEvent.java
│   │   └── DocumentDeletedEvent.java
│   │
│   └── exception/                        ← wyjątki domenowe
│       └── DocumentNotFoundException.java
│
├── application/
│   ├── DocumentService.java              ← implements wszystkie UseCase'y
│   └── (helpery, walidatory, polityki - jeśli logika rośnie)
│
├── infrastructure/
│   ├── persistence/
│   │   ├── DocumentJpaEntity.java        ← @Entity, @TenantId
│   │   ├── DocumentJpaRepository.java    ← Spring Data JPA interface
│   │   ├── DocumentEntityMapper.java     ← Document (domain) ↔ DocumentJpaEntity
│   │   └── DocumentRepositoryAdapter.java ← implements DocumentRepository (port OUT)
│   │
│   └── (inne adaptery - np. external API, jeśli moduł takich potrzebuje)
│
└── api/
    ├── DocumentController.java           ← REST endpoints
    ├── dto/
    │   ├── DocumentResponse.java         ← record
    │   ├── UploadDocumentRequest.java    ← record
    │   └── DocumentStatusResponse.java
    │
    └── DocumentApiMapper.java            ← DTO ↔ domain.model
```

### Co jest opcjonalne

- `api/` - tylko dla modułów wystawiających REST (business modules). Orchestrator modules (`indexing`, `rag`, `vector`, `file`) nie mają `api/`.
- `event/` - tylko jeśli moduł publikuje eventy (jak `document/`).
- `exception/` w domain - tylko jeśli moduł rzuca własne wyjątki domenowe.
- `domain/model/` może być spłaszczony do `domain/` jeśli model jest mały (1-2 klasy).

### Czego TU NIE MA

- Folderu `dto/` na top-level - DTOs są **per warstwa**: DTO REST w `api/dto/`, mapping między domain a JPA entity to nie DTO tylko mapper.
- Folderu `repository/` w pakiecie root - repozytoria JPA są w `infrastructure/persistence/`.
- Folderu `entity/` na top-level - JPA entity to detal infrastruktury, siedzi w `infrastructure/persistence/`. Model domenowy jest w `domain/model/`.

---

## 4. Domain - co tam siedzi

**Pure Java.** Zero importów spoza `domain/`, `shared/`, JDK.

### `domain/model/`

Modele domenowe to **agregaty + value objects**. Pisane jako:
- `record` - dla immutable agregatów (preferowane)
- klasa - jeśli agregat ma mutable state albo skomplikowaną logikę

**Przykładowe nazwy klas:** `Document`, `ChatSession`, `Message`, `Chunk`, `RagAnswer`, `Citation`.

**Co NIE może się tu pojawić:**
- `@Entity`, `@Id`, `@Column`, `@TenantId` (te są w `infrastructure/persistence/`)
- `@Component`, `@Service`, `@Autowired` (Spring DI - to detal aplikacji)
- importy `jakarta.persistence.*`, `org.springframework.*`, `org.hibernate.*`

### `domain/port/in/`

Interfejsy "wejściowe" - definiują **co świat może zrobić z modułem**.

Konwencja nazewnicza: `<Czasownik>+<Rzeczownik>UseCase`, np. `UploadDocumentUseCase`, `SearchChunksUseCase`, `AnswerQuestionUseCase`.

Każdy use case to **jeden interfejs z jedną metodą** (Command pattern - łatwo testować, łatwo wymieniać). Jeśli kilka operacji jest blisko logicznie - można grupować, ale domyślnie jeden use case = jedna metoda.

**Przykład:**
```
UploadDocumentUseCase {
    DocumentId upload(UploadDocumentCommand command);
}
```

Command zawiera input (filename, mimeType, bytes). Zwraca ID. Dla read-side: `ListDocumentsQuery` → `List<DocumentSummary>`.

### `domain/port/out/`

Interfejsy "wyjściowe" - **czego domena potrzebuje od świata**.

Konwencja: `<Rzeczownik>Repository` lub `<Rzeczownik>Port`, np. `DocumentRepository`, `FileStoragePort`, `EmbeddingPort`.

**Metoda na repozytorium operuje na domain model**, nie na JPA entity:
```
DocumentRepository {
    Document save(Document document);
    Optional<Document> findById(DocumentId id);
    List<Document> findByTenant();
    void delete(DocumentId id);
}
```

Implementacja w `infrastructure/` mapuje domain ↔ JPA entity przy każdej operacji.

### `domain/event/`

Eventy publikowane przez moduł. Konwencja: `<Past tense>Event`, np. `DocumentUploadedEvent`, `IndexingCompletedEvent`, `MessageSentEvent`.

**Event jako record**, z minimum danych potrzebnych konsumentowi (ID, tenant_id, timestamp). Bez całych agregatów - inaczej konsument zna zbyt wiele.

Szczegóły: [`events.md`](events.md).

---

## 5. Application - use case'y i serwisy

### `<Moduł>Service.java`

Główna klasa - implementuje **wszystkie** use case'y z `domain/port/in/`:

```
DocumentService implements
    UploadDocumentUseCase,
    DeleteDocumentUseCase,
    ListDocumentsUseCase,
    GetDocumentStatusUseCase {
    ...
}
```

Wstrzykiwane przez konstruktor (Spring `@Service`):
- porty OUT (`DocumentRepository`, ewentualnie `FileStoragePort` z modułu `file`)
- porty IN sąsiednich modułów (np. `chat/ChatService` może wstrzyknąć `rag/AnswerQuestionUseCase`)
- `ApplicationEventPublisher` (Spring) - do publikowania eventów

### Transakcyjność

`@Transactional` **na poziomie metody service**, nie kontrolera. Domyślnie `readOnly = true` dla zapytań, jawne `@Transactional` dla zapisów.

### Co tu NIE należy

- **Walidacja formatu JSON / nullowości pól** - to robi `api/` (Jakarta Validation w DTO)
- **Mapowanie domain ↔ JPA entity** - to robi `infrastructure/persistence/`
- **Wywoływanie repozytoriów JPA bezpośrednio** - tylko przez porty OUT

### Polityki, walidatory

Jeśli logika rośnie, wyciągnij do osobnych klas w `application/`:
- `ChunkingPolicy.java` - decyzja jak chunkować dokument
- `PromptBuilder.java` - budowa promptu z kontekstu
- `MessageValidator.java` - walidacja wiadomości (długość, content)

Te klasy są też package-private dla modułu - nikt z zewnątrz ich nie widzi.

---

## 6. Infrastructure - adaptery

### `infrastructure/persistence/`

Tu siedzi JPA. Wszystkie zaklęcia Hibernate, `@Entity`, `@TenantId`.

**Cztery klasy per agregat:**

1. **`<Agregat>JpaEntity.java`** - JPA encja z `@Entity`, `@Table`, `@Column`, `@TenantId`. Lombok `@Getter`, `@Setter`, `@NoArgsConstructor` (Hibernate wymaga).

2. **`<Agregat>JpaRepository.java`** - Spring Data interface `extends JpaRepository<JpaEntity, Long>`. Metody typu `findByFilename(String)` - tu derived queries.

3. **`<Agregat>EntityMapper.java`** - statyczne metody `toDomain(JpaEntity)` i `toJpaEntity(Domain)`. Jeśli mapowanie skomplikowane → Spring `@Component` z metodami.

4. **`<Agregat>RepositoryAdapter.java`** - `@Component` implementujący port OUT z domeny (`DocumentRepository`). Wstrzykuje `JpaRepository` + `EntityMapper`. Delegacja + mapowanie.

### Inne adaptery

W tym samym `infrastructure/` mogą siedzieć adaptery dla:
- **Zewnętrzne API HTTP** - `infrastructure/external/SomeApiClient.java` (np. webhook do CRM)
- **Kolejka** - `infrastructure/queue/RabbitMqAdapter.java` (post-MVP)
- **Cache** - `infrastructure/cache/RedisCacheAdapter.java` (post-MVP)
- **File system** - `infrastructure/storage/LocalFileStorageAdapter.java` (moduł `file/`)
- **S3** - `infrastructure/storage/S3FileStorageAdapter.java` (moduł `file/`)

Konwencja: jeden subpakiet per technologia / źródło danych.

---

## 7. API - adapter wejściowy (REST)

### `<Moduł>Controller.java`

Spring `@RestController`. Tylko dla modułów wystawiających REST.

**Co robi:**
- Mapuje HTTP → command/query (przez `<Moduł>ApiMapper`)
- Waliduje DTO requestu (`@Valid` + Jakarta Validation w DTO)
- Woła use case przez port IN: `uploadDocumentUseCase.upload(command)`
- Mapuje wynik → response DTO
- Zwraca `ResponseEntity<DocumentResponse>`

**Wstrzykuje wyłącznie porty IN** z domeny modułu - nigdy `DocumentService` bezpośrednio.

### `api/dto/`

DTO request i response jako Java records:
```
record UploadDocumentRequest(
    @NotBlank String filename,
    @NotNull MultipartFile file
) {}

record DocumentResponse(
    Long id,
    String filename,
    String status,
    Instant uploadedAt
) {}
```

### `api/<Moduł>ApiMapper.java`

Statyczne metody:
- `UploadDocumentCommand toCommand(UploadDocumentRequest request)`
- `DocumentResponse toResponse(Document document)`

Mapowanie tylko między DTO a domain model. Nigdy nie dotyka JPA entity.

---

## 8. Konwencje nazewnicze

| Element | Konwencja | Przykłady |
|---------|-----------|-----------|
| Domain model | rzeczownik | `Document`, `ChatSession`, `Message`, `Chunk` |
| Value object - ID | `<Nazwa>Id` | `DocumentId`, `TenantId`, `ChatSessionId` |
| Port IN | `<Czasownik><Rzeczownik>UseCase` | `UploadDocumentUseCase`, `SearchChunksUseCase` |
| Command | `<Czasownik><Rzeczownik>Command` | `UploadDocumentCommand`, `SendMessageCommand` |
| Query | `<Czasownik><Rzeczownik>Query` | `ListDocumentsQuery`, `GetSessionHistoryQuery` |
| Port OUT (repo) | `<Rzeczownik>Repository` | `DocumentRepository`, `ChatRepository` |
| Port OUT (inny) | `<Rzeczownik>Port` | `FileStoragePort`, `EmbeddingPort`, `LlmPort` |
| Service | `<Moduł>Service` | `DocumentService`, `ChatService` |
| Event | `<Past tense>Event` | `DocumentUploadedEvent`, `IndexingFailedEvent` |
| JPA entity | `<Agregat>JpaEntity` | `DocumentJpaEntity` |
| JPA repo (Spring Data) | `<Agregat>JpaRepository` | `DocumentJpaRepository` |
| Adapter (port OUT impl) | `<Agregat>RepositoryAdapter` lub `<Tech><Port>Adapter` | `DocumentRepositoryAdapter`, `LocalFileStorageAdapter` |
| Mapper domain ↔ JPA | `<Agregat>EntityMapper` | `DocumentEntityMapper` |
| Mapper domain ↔ DTO | `<Moduł>ApiMapper` | `DocumentApiMapper` |
| Controller | `<Moduł>Controller` | `DocumentController` |
| DTO request | `<Akcja>Request` | `UploadDocumentRequest`, `SendMessageRequest` |
| DTO response | `<Rzeczownik>Response` | `DocumentResponse`, `ChatSessionResponse` |
| Exception | `<Coś>Exception` | `DocumentNotFoundException`, `InvalidPromptException` |

Konsekwentne nazewnictwo = łatwa nawigacja w IDE (np. `Cmd+O` "UseCase" pokazuje wszystkie publiczne API).

---

## 9. Visibility - co eksportujemy

### Reguła naczelna
**Public = tylko to, co naprawdę musi być widoczne dla innych modułów.** Reszta - package-private.

### Co jest public

| Klasa | Visibility | Dlaczego |
|-------|------------|----------|
| `domain.port.in.*UseCase` | **public** | inne moduły to wołają |
| `domain.model.*` (modele, ID, enums) | **public** | są w sygnaturach use case'ów |
| `domain.event.*` | **public** | inne moduły mogą słuchać |
| `domain.exception.*` | **public** | bo lecą przez warstwy do API |

### Co jest package-private

| Klasa | Visibility | Dlaczego |
|-------|------------|----------|
| `domain.port.out.*` | **package-private** | tylko adaptery w tym module je implementują |
| `application.<Moduł>Service` | **package-private** | implementacja, dostęp przez port IN |
| `application.<inne helpery>` | **package-private** | wewnętrzne |
| `infrastructure.*` | **package-private** | adaptery, detal implementacji |
| `api.*Controller` | **public** (Spring wymaga) | ale i tak nikt z innych modułów nie powinien wołać kontrolera |
| `api.dto.*` | **package-private** | DTOs są lokalne dla kontrolera |

### Spring Modulith package-info.java

W root modułu plik `package-info.java` z adnotacją:
```
@ApplicationModule(
    allowedDependencies = { "shared", "tenancy", "file::domain.port.in", "ai" }
)
package com.multibot.document;
```

Mówi Modulith: ten moduł może widzieć moduły `shared`, `tenancy`, `ai` w całości, ale z `file` tylko subpaczki `domain.port.in`. Test sprawdza ten kontrakt.

---

## 10. Komunikacja między modułami

Dwa sposoby:

### Synchronicznie - przez port IN sąsiada

Moduł A wstrzykuje use case modułu B:
```
DocumentService dostaje wstrzyknięty StoreFileUseCase (z modułu file/)
```

Działa jak DI - moduł A wie tylko o interfejsie z `b.domain.port.in`, nie o `BService`.

Plus: synchronicznie, transakcja A nie sięga do B (różne moduły = różne transakcje), prosto.

Użycie: gdy potrzebujesz **wyniku** wywołania (np. document.upload → file.store → dostajesz `StoredFileId`).

### Asynchronicznie - przez event

Moduł A publikuje event, moduł B słucha. Bez bezpośredniej zależności w kodzie.

Plus: pełna luzo-zależność, łatwa skalowalność, transactional outbox (Modulith Events).

Użycie: gdy A "ogłasza fakt" i kilka modułów może chcieć zareagować (np. `DocumentUploadedEvent` → `indexing` reaguje, `analytics` reaguje, w przyszłości `webhook` reaguje).

Szczegóły → [`events.md`](events.md).

---

## 11. Anti-patterny - czego NIE robić

| Anti-pattern | Dlaczego źle | Co zamiast |
|--------------|--------------|------------|
| Domain importuje `jakarta.persistence.*` | Domena wie o JPA → wyciekanie infrastruktury w głąb | Trzymaj JPA w `infrastructure/persistence/`, mapuj |
| Service zwraca JPA entity | API dostaje JPA entity, wydaje internal state | Service zwraca domain model, API mapuje na DTO |
| Controller wstrzykuje `DocumentService` | Łamie hexagon - controller wie o impl | Controller wstrzykuje `UploadDocumentUseCase` (interfejs) |
| Moduł A importuje `b.infrastructure.*` | Łamie granicę modułu - sięga do detalu B | Importuje TYLKO `b.domain.port.in.*` |
| `findAll()` bez TenantContext | Wyciek danych między tenantami | `@TenantId` na JPA entity + Hibernate filtruje (patrz [multi-tenancy.md](../multi-tenancy.md)) |
| Logika biznesowa w kontrolerze | Brak testowalności, łamie SoC | Logika w `Service` (application), kontroler tylko mapuje |
| Domain model z `@Component` / `@Autowired` | Domena nie jest komponentem Spring | Domain to czyste POJO, Spring tylko w application/infrastructure |
| Mapper jako `@Component` z DI | Niepotrzebny narzut, mappery to czyste funkcje | Statyczne metody na klasie mapper |
| JPA entity = domain model (z `@TenantId` w domain) | Łamie hexagon, miesza warstwy | Dwie osobne klasy + mapper |
| Repository (port OUT) zwraca `Page<JpaEntity>` | Wycieka JPA do domeny | Port OUT zwraca `List<Document>` lub `PaginatedResult<Document>` |
| Event zawiera całą encję | Konsument zna zbyt wiele, zmiana modelu psuje kontrakt | Event = tylko ID, tenant_id, timestamp + minimalny payload |
| Kontroler woła kilka use case'ów i sklada wynik | Logika "wycieka" do kontrolera | Stwórz wyższy use case który orkiestruje pod spodem |

---

## 12. Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Strategia ogólna backendu + drzewo pakietów | [`overview.md`](overview.md) |
| Co konkretnie robi każdy moduł infrastrukturalny | [`modules-infrastructure.md`](modules-infrastructure.md) |
| Co konkretnie robi każdy moduł biznesowy | [`modules-business.md`](modules-business.md) |
| Spring Modulith + jak działają eventy | [`events.md`](events.md) |
| Konfiguracja, błędy, testy | [`operations.md`](operations.md) |
| Strategia multi-tenancy | [`../multi-tenancy.md`](../multi-tenancy.md) |
