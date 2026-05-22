# Spring Modulith + komunikacja przez eventy

Spring Modulith odpowiada za dwie rzeczy w naszej aplikacji:
1. **Enforce'owanie granic modułów** - test w buildzie failuje jeśli ktoś naruszy `allowedDependencies`
2. **Eventy między modułami** - asynchroniczna komunikacja z transactional outbox

---

## 1. Spring Modulith - co dostajemy

| Funkcjonalność | Co daje | Czy używamy w MVP |
|----------------|---------|-------------------|
| **Module detection** | `ApplicationModules.of(MultibotApplication.class)` - skanuje pakiety, buduje model modułów | ✅ tak (auto) |
| **Dependency enforcement** | Test `verify()` - failuje jeśli moduł narusza `allowedDependencies` | ✅ tak |
| **`@ApplicationModuleListener`** | Adnotacja - listener async + transactional + zachowanie TenantContext | ✅ tak |
| **Transactional outbox** | Eventy zapisane do tabeli przed wysłaniem - failsafe, retry | ✅ tak |
| **Documentation generation** | Generowanie diagramów modułów (asciidoc / PlantUML) | opcjonalnie |
| **Observability** | Metryki publikacji/konsumpcji eventów (Micrometer) | post-MVP |
| **Module integration tests** | `@ApplicationModuleTest` - test pojedynczego modułu z izolacją | ✅ tak |

---

## 2. Setup

### Dependencies (Maven)

```
spring-modulith-starter-core
spring-modulith-starter-jpa          # outbox w JPA (potrzebujemy w runtime)
spring-modulith-events-api           # API dla @ApplicationModuleListener
spring-modulith-events-core
spring-modulith-starter-test         # @ApplicationModuleTest, scenario testing
```

`spring-modulith-starter-jpa` tworzy tabelę `event_publication` przez Flyway (lub własną migrację - patrz [`operations.md`](operations.md)).

### Deklaracja modułu - `package-info.java`

W każdym module:

```
@ApplicationModule(
    displayName = "Document Management",
    allowedDependencies = { "shared", "tenancy", "file", "vector" }
)
package com.multibot.document;
```

Dla "open" infrastructure modules (shared, tenancy, config, ai) bez `allowedDependencies` - dostępne dla wszystkich.

### Test enforcement - jeden test, raz na zawsze

```
class ApplicationModulesTest {
    @Test
    void verifyModularBoundaries() {
        ApplicationModules.of(MultibotApplication.class).verify();
    }
}
```

Ten test:
- Sprawdza wszystkie zależności między pakietami
- Failuje jeśli moduł `chat` importuje z `document.infrastructure.persistence`
- Failuje jeśli powstał cykl
- Failuje jeśli moduł nie istnieje w `allowedDependencies`

Uruchamiany w CI - bez tego ludzie zaczynają łamać granice nieświadomie.

---

## 3. Eventy - jak je piszemy

### Konwencja

Event jako **record** w `<moduł>.domain.event.*`. Nazwa: **past tense + Event**.

```
document/domain/event/DocumentUploadedEvent.java
indexing/domain/event/IndexingCompletedEvent.java
chat/domain/event/MessageSentEvent.java
```

### Co powinno być w evencie

**Minimum potrzebne konsumentowi** - nic więcej.

```
DocumentUploadedEvent(
    DocumentId documentId,
    TenantId tenantId,
    String filename,
    String mimeType,
    Instant uploadedAt
)
```

Konsument który chce więcej - wywoła `document.GetDocumentUseCase(documentId)` żeby pobrać pełne dane.

### Co NIE powinno być w evencie

- **Cały agregat** (`DocumentUploadedEvent(Document document)`) - konsument zna zbyt wiele, każda zmiana modelu psuje kontrakt
- **JPA entity** - łamie hexagon (JPA entity to detal infrastructure)
- **DTO API** (`DocumentResponse`) - to detal warstwy api/, nie domeny
- **Wrażliwe dane** - jeśli logujemy eventy, nie chcemy haseł / kluczy / PII w logu

### `TenantId` w każdym evencie

**Zasada:** każdy event biznesowy ma pole `TenantId`. Dlaczego:
- Listener może chcieć weryfikować że event dotyczy "swojego" tenanta (defense in depth)
- Logi z eventów łatwiej śledzić per tenant
- Jeśli kiedyś rozjedziemy event bus na osobny serwis (Kafka) - tenant_id jest w payloadzie

---

## 4. Publikowanie eventów

### Sposób 1: `ApplicationEventPublisher` (preferowany)

```
class DocumentService implements UploadDocumentUseCase {
    private final ApplicationEventPublisher events;
    private final DocumentRepository repo;

    @Transactional
    public DocumentId upload(UploadDocumentCommand cmd) {
        Document doc = ...; // create
        repo.save(doc);

        events.publishEvent(new DocumentUploadedEvent(
            doc.id(),
            doc.tenantId(),
            doc.filename(),
            doc.mimeType(),
            doc.uploadedAt()
        ));

        return doc.id();
    }
}
```

### Co się dzieje za kulisami

1. **W trakcie transakcji:** Spring Modulith przechwytuje event i **zapisuje go do tabeli `event_publication`** (outbox)
2. **Po commicie transakcji:** Modulith oznacza event jako "do wysłania"
3. **Async:** worker thread czyta nieobsłużone eventy z tabeli i wysyła do listenerów
4. **Po pomyślnej obsłudze:** event w tabeli oznaczony jako "completed" (kolumna `completion_date`)

### Konsekwencje

- **Event wysłany TYLKO po commicie** - jeśli transakcja rollback, event nie idzie. Brak inconsistency "zapisane w bazie ale konsument nie dostał".
- **At-least-once delivery** - jeśli worker padł między oznaczeniem i wysłaniem, event będzie wysłany ponownie po restarcie
- **Trwałość** - eventy ocalają restart aplikacji (siedzą w bazie)

---

## 5. Słuchanie eventów

### `@ApplicationModuleListener` (preferowane)

```
class IndexingEventListener {

    @ApplicationModuleListener
    void onDocumentUploaded(DocumentUploadedEvent event) {
        // TenantContext automatycznie ustawiony na event.tenantId()
        indexDocumentUseCase.index(event.documentId());
    }
}
```

### Co daje `@ApplicationModuleListener`

Pod spodem to:
- `@TransactionalEventListener(phase = AFTER_COMMIT)` - event obsługiwany po commicie publishera
- `@Async` - w osobnym wątku
- **Automatyczna propagacja TenantContext** - Modulith zapisuje `tenant_id` razem z eventem (z `TenantContext.get()` w momencie publikacji) i ustawia go w wątku listenera
- Outbox - retry przy błędach
- Logging - publikacja i konsumpcja widoczne w metrykach

### vs zwykły `@EventListener`

| | `@EventListener` | `@ApplicationModuleListener` |
|---|------------------|------------------------------|
| Synchroniczny | tak (domyślnie) | nie |
| Outbox | NIE | tak |
| TenantContext propagacja | NIE | tak |
| Retry przy błędzie | NIE | tak |
| Transactional safety | tylko z `@TransactionalEventListener` | tak (z definicji) |

**Reguła:** w multibocie używamy WYŁĄCZNIE `@ApplicationModuleListener` dla eventów cross-module.

---

## 6. Transactional outbox - co siedzi w bazie

### Tabela `event_publication`

| Kolumna | Opis |
|---------|------|
| `id` | UUID eventu |
| `listener_id` | identyfikator listenera (`com.multibot.indexing.IndexingEventListener.onDocumentUploaded`) |
| `event_type` | FQCN klasy eventu (`com.multibot.document.domain.event.DocumentUploadedEvent`) |
| `serialized_event` | JSON payload eventu |
| `publication_date` | kiedy opublikowany |
| `completion_date` | kiedy obsłużony (NULL = nieobsłużony) |

**Jeden event publikowany do N listenerów** = N rekordów w tabeli (jeden per listener).

### Co się dzieje przy błędach

Listener rzuca wyjątek:
- Spring Modulith łapie i NIE oznacza eventu jako completed
- Domyślnie: BRAK auto-retry (event zostaje w "pending" stanie)
- Trzeba ręcznie: `EventPublicationRegistry.findIncompletePublications()` + restart obsługi

**Konsekwencja praktyczna:** krytyczne błędy w listenerach trzeba zlogowanie + alert + ręczna interwencja.

### Republishing

Po restarcie aplikacji - Spring Modulith ma opcję auto-republikacji nieobsłużonych eventów:

```
spring.modulith.republish-outstanding-events-on-restart=true
```

Włączamy w `application.yml`. Zapewnia że eventy nie giną przy crashu.

---

## 7. Mapa eventów w MVP

| Event | Publisher | Listener(y) | Po co |
|-------|-----------|-------------|-------|
| `DocumentUploadedEvent` | `document/` | `indexing/` | Wyzwolenie async indeksacji |
| `IndexingCompletedEvent` | `indexing/` | `document/` | Update statusu na READY + `indexedAt` |
| `IndexingFailedEvent` | `indexing/` | `document/` | Update statusu na FAILED + zapisanie błędu |
| `DocumentDeletedEvent` | `document/` | `indexing/` (cleanup), później `analytics/` | Kaskadowe usunięcie chunków (drugi mechanizm - obok bezpośredniego wywołania `vector.DeleteChunksByDocumentUseCase` - ale przez event można też powiadomić inne moduły) |

### Post-MVP

| Event | Publisher | Listener(y) | Po co |
|-------|-----------|-------------|-------|
| `MessageSentEvent` | `chat/` | `analytics/` (przyszły moduł) | Statystyki, koszty per tenant |
| `TenantCreatedEvent` | `tenant/` | `provisioning/` (przyszły) | Setup nowego tenanta (seed, plan, etc.) |
| `TenantSuspendedEvent` | `tenant/` | wszystkie moduły | Czyszczenie cache, anulowanie jobów |

### Czego NIE robimy przez eventy w MVP

- **Update statusu dokumentu z indexing → document** - mogłoby być bezpośrednim wywołaniem (sync) zamiast eventu, ale używamy eventu dla luźnej zależności. **Świadomy wybór.**
- **document → chat** - chat nie wie o documentach. Brak takich eventów.

---

## 8. Testy eventów

Spring Modulith daje DSL do testowania scenariuszy event-driven:

```
@ApplicationModuleTest
class DocumentUploadScenarioTest {

    @Test
    void documentUpload_triggersIndexing(Scenario scenario) {
        scenario.publish(new DocumentUploadedEvent(...))
            .andWaitForEventOfType(IndexingCompletedEvent.class)
            .matching(event -> event.documentId().equals(expectedId))
            .toArriveAndVerify(event -> {
                assertThat(event.chunksCreated()).isGreaterThan(0);
            });
    }
}
```

**Co testuje:**
- Publikuje event ręcznie
- Czeka aż listenerzy zareagują i opublikują kolejny event
- Asercje na finalnym evencie

**Co NIE testuje** (mock all the things):
- Realne LLM API
- Realne pliki na dysku
- Inne moduły w stanie integracji

**Cleanup:** każdy test ma czystą tabelę `event_publication` (Modulith dostarcza `@DirtiesContext` / cleanup helper).

---

## 9. Idempotency listenerów - krytyczne

**Najważniejsza sekcja tego dokumentu.** Spring Modulith zapewnia **at-least-once delivery** - eventy mogą zostać dostarczone więcej niż raz. To jest **fundamentalny problem distributed systems**, którego Modulith **NIE rozwiązuje za nas** - musimy sami projektować idempotentne listenery.

### Scenariusz problemu

1. `DocumentUploaded` event w tabeli `event_publication`, `completion_date = NULL`
2. Worker wywołuje `IndexingEventListener.onDocumentUploaded()`
3. Listener:
   - Tworzy `IndexingJob` (rekord w bazie)
   - Wywołuje OpenAI embedding API dla 50 chunków (kosztuje pieniądze)
   - Zapisuje 50 chunków do pgvector
   - **CRASH** ← aplikacja pada tutaj
4. `completion_date` nadal NULL, listener nie skończył
5. Restart aplikacji
6. Modulith widzi `WHERE completion_date IS NULL` → re-deliveruje event
7. Listener wykonuje się **drugi raz**:
   - Tworzy **drugi** `IndexingJob` (duplikat!)
   - Wywołuje OpenAI **drugi raz** (drugi koszt!)
   - Zapisuje **kolejne 50 chunków** → teraz 100 chunków zamiast 50 → **RAG zwraca duplikaty**

Najgorsze: **silent corruption**. Aplikacja działa, nikt nie widzi błędu, ale wyniki są zepsute.

### Filozoficznie

W distributed systems są tylko dwie opcje delivery:
- **At-most-once** - eventy mogą zginąć (jak in-memory)
- **At-least-once** - eventy mogą się powtórzyć (jak Modulith outbox)

**Exactly-once nie istnieje.** Wszystkie "exactly-once" systemy w rzeczywistości robią at-least-once + idempotent consumer.

### Trzy wzorce idempotency

#### Wzorzec 1: Check przed wykonaniem

Listener sprawdza czy efekt już istnieje, jeśli tak - skip.

```
@ApplicationModuleListener
void onDocumentUploaded(DocumentUploadedEvent event) {
    if (indexingJobRepo.existsByDocumentId(event.documentId())) {
        log.info("Event already processed, skipping");
        return;
    }
    // ... reszta logiki
}
```

| Plus | Minus |
|------|-------|
| prosty | tracimy postęp - jeśli crash w połowie, zaczynamy od zera następnym razem |

#### Wzorzec 2: Stan agregatu jako "marker"

Listener używa pól stanu w bazie żeby wiedzieć "do której fazy doszedłem".

```
@ApplicationModuleListener
void onDocumentUploaded(DocumentUploadedEvent event) {
    Document doc = documentRepo.findById(event.documentId());
    if (doc.status() != PENDING) {
        log.info("Document not in PENDING state, skipping");
        return;
    }
    // Atomowy update PENDING → PROCESSING (z WHERE status='PENDING')
    documentRepo.transitionToProcessing(event.documentId());
    // ... reszta
}
```

| Plus | Minus |
|------|-------|
| bazuje na stanie biznesowym | wymaga przemyślenia jakie stany są "safe to retry" |

#### Wzorzec 3: Cleanup + retry

Listener zawsze czyści wszystko z poprzedniej (niepełnej) próby przed nowym startem.

```
@ApplicationModuleListener
void onDocumentUploaded(DocumentUploadedEvent event) {
    // Wyczyść poprzednią próbę (jeśli była)
    vectorStore.deleteChunksByDocument(event.documentId());
    indexingJobRepo.deleteByDocumentId(event.documentId());

    // Świeży start
    IndexingJob job = createNewJob(...);
    // ... processing ...
}
```

| Plus | Minus |
|------|-------|
| zawsze czysty stan, brak duplikatów | marnujemy poprzednią pracę (np. embedding API calls) |

### Strategia dla multibot - mapa eventów

Dla naszej aplikacji proponowane strategie:

| Event → Listener | Strategia | Uzasadnienie |
|------------------|-----------|-------------|
| `DocumentUploadedEvent` → `IndexingEventListener` | **Wzorzec 2 + 3** (kombinacja) | Sprawdź status (skip jeśli READY), wyczyść poprzednią próbę, zacznij od zera. Duplikaty w pgvector zepsułyby RAG. |
| `IndexingCompletedEvent` → `DocumentStatusUpdater` | **Wzorzec 2** | UPDATE z `WHERE status='PROCESSING'` - drugi update nic nie zrobi |
| `IndexingFailedEvent` → `DocumentStatusUpdater` | **Wzorzec 2** | jw. |
| `DocumentDeletedEvent` → `VectorCleaner` | **Wzorzec 1** | Sprawdź czy chunki istnieją, jeśli nie - skip (delete jest naturalnie idempotent) |

#### Pełny przykład dla indeksacji

```
@ApplicationModuleListener
void onDocumentUploaded(DocumentUploadedEvent event) {
    Document doc = documentRepo.findById(event.documentId());

    // Wzorzec 2: skip jeśli już ukończone
    if (doc.status() == READY) {
        log.info("Document {} already indexed, skipping", event.documentId());
        return;
    }

    // Wzorzec 3: cleanup poprzedniej próby (jeśli crash w środku)
    vectorStore.deleteChunksByDocument(event.documentId());
    indexingJobRepo.deleteByDocumentId(event.documentId());

    // Świeży start
    documentRepo.transitionToProcessing(event.documentId());
    IndexingJob job = createNewJob(event);

    try {
        processDocument(event, job);
        // publikuje IndexingCompletedEvent
    } catch (Exception e) {
        // publikuje IndexingFailedEvent
    }
}
```

### Idempotency dla zewnętrznych API

Co jeśli listener wysyła webhook / call do zewnętrznego serwisu (np. CRM klienta)? Re-delivery = wywołanie API dwa razy. Skutki uboczne (email do klienta, mutacja danych w cudzym systemie) nie da się "odpukać".

Rozwiązanie: **idempotency key**:
- Generujemy unikalny klucz per event (np. UUID z `event_publication.id`)
- Wysyłamy w nagłówku `Idempotency-Key: <uuid>`
- Zewnętrzne API (jeśli obsługuje) wykrywa duplikat i ignoruje

Jeśli zewnętrzny serwis tego nie obsługuje - musimy sami trackować w bazie "co już wysłaliśmy" (tabela `sent_webhooks` z `event_id` UNIQUE). Wtedy listener:
```
if (sentWebhooksRepo.existsByEventId(eventId)) return;
sendWebhook(...);
sentWebhooksRepo.save(new SentWebhook(eventId, ...));
```

### Zasady dla każdego nowego listenera

Przed merge'em nowego listenera, autor odpowiada na pytania:

1. **Co się stanie jeśli ten listener wykona się 2 razy?** Jeśli odpowiedź to "duplikaty / podwójne emaile / zepsute dane" - listener NIE jest gotowy
2. **Czy używam stanu bazy (Wzorzec 2), check istnienia (Wzorzec 1) czy cleanup (Wzorzec 3)?** Musi być świadoma decyzja
3. **Czy są skutki uboczne poza moim modułem?** (zewnętrzne API, email, webhook) - wymagają idempotency key lub trackingu
4. **Czy są testy potwierdzające idempotency?** Test: wywołaj listener z tym samym eventem 2x → asercja że efekt jest taki sam jak po 1 wywołaniu

### Co Modulith **nie zrobi** za nas

- ❌ Nie wykryje że listener "już się wykonał" (z perspektywy biznesowej)
- ❌ Nie cofnie skutków ubocznych
- ❌ Nie zatrzyma retry przy błędach niedeterministycznych (np. 500 z OpenAI)
- ❌ Nie zaalarmuje gdy event utknie pending (musimy sami monitorować)

### Co Modulith **robi** za nas

- ✅ Zapisuje event w tabeli przed wysłaniem (przeżywa restart)
- ✅ Po restarcie wznawia obsługę pending eventów
- ✅ Oznacza completion gdy listener wykona się bez wyjątku
- ✅ Propaguje TenantContext do listenera

---

## 10. Pitfalls i zasady

### Czerwone zasady

1. **Eventy są domain events, nie technical events.** `DocumentUploadedEvent` ✅, `DatabaseRowInsertedEvent` ❌.
2. **Event jest faktem, nie poleceniem.** `IndexingCompletedEvent` (fakt) ✅, `IndexDocumentEvent` (polecenie) ❌ - polecenia idą przez port IN.
3. **Event jest immutable.** Record z `final` polami. Po publikacji nikt go nie modyfikuje.
4. **Event nie zawiera całego agregatu.** Tylko ID + minimum potrzebne konsumentowi.
5. **Event zawiera tenant_id.** Zawsze.
6. **Listener nie zakłada kolejności.** Mogą dojść w innej kolejności niż wysłane - design idempotent listeners.
7. **Listener jest idempotent.** Jak ten sam event dojdzie 2x - aplikacja działa poprawnie (at-least-once delivery). Szczegóły w sekcji 9 - **to jest tematyka krytyczna**.

### Pułapki

- **Cykl przez event** - moduł A publikuje event, moduł B słucha, B publikuje event, A słucha. Spring Modulith pozwala (bo nie ma bezpośredniej zależności w kodzie), ale logicznie cykl. Łatwo o nieskończoną pętlę - **uważać przy projektowaniu listenerów**.
- **Eventual consistency okno** - między publish a consume jest okno (np. 100ms-2s). Frontend pollujący statusu zobaczy `PENDING` przez chwilę nawet jak indeksacja sie zaczęła. Zaprojektowane: status `PENDING` → `PROCESSING` (sync update) → `READY` (event-driven).
- **Listener throws** - bez retry musisz monitorować failed eventów. Logger + alert na `completion_date IS NULL AND publication_date < now() - 1 hour`.
- **Test isolation** - tabela `event_publication` jest persistent. Bez czyszczenia między testami fly tests się przeplatają.
- **Async w transakcji** - listener leci AFTER_COMMIT, czyli **na zewnątrz** transakcji publishera. Jak listener chce zapisać coś do bazy - własna transakcja. To dobre, ale trzeba pamiętać.

---

## 11. Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Strategia ogólna backendu | [`overview.md`](overview.md) |
| Wzorzec hexagonalny | [`hexagonal-pattern.md`](hexagonal-pattern.md) |
| Mapa eventów rozłożona na moduły | [`modules-business.md`](modules-business.md) |
| Konfiguracja Spring Modulith właściwe property | [`operations.md`](operations.md) |
| Flow uploadu z eventami (sequence diagram) | [`../../flows/document-upload.md`](../../flows/document-upload.md) |
