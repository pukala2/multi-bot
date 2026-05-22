# Multi-tenancy - strategia izolacji tenantów

Najważniejszy dokument architektoniczny. Decyduje jak wszystko inne w kodzie wygląda.

**Cel:** zaprojektować izolację tenantów tak, żeby w MVP działać jako "single-tenant" (`tenant_id=1` hardcoded), a przejście na realny multi-tenant było zmianą **konfiguracji**, nie refactorem.

---

## 1. Strategia - co wybieramy

Trzy typowe podejścia do multi-tenancy w aplikacjach SaaS:

| Podejście | Izolacja danych | Złożoność | Skalowalność | Koszt |
|-----------|-----------------|-----------|--------------|-------|
| **Pool model** (shared DB, shared schema, `tenant_id` kolumna) | Logiczna - na poziomie aplikacji | Niska | Łatwa do dziesiątek tysięcy tenantów | Niski |
| **Schema-per-tenant** | Fizyczna - osobny schemat per tenant | Średnia | Ograniczona (Postgres ~tysiące schematów) | Średni |
| **Database-per-tenant** | Pełna - osobna baza | Wysoka | Operacyjnie trudna | Wysoki |

**Decyzja: Pool model.**

Każda tabela ma kolumnę `tenant_id`. Jedna instancja aplikacji, jedna baza, jeden schemat. Izolacja na poziomie kodu - przez systematyczne filtrowanie po `tenant_id` w każdym query.

**Uzasadnienie:**
- Prostota operacyjna: jeden deployment, jeden backup, jedne migracje Flyway
- Wystarczy na pierwsze 50-500 klientów
- Najtańsze - dziesiątki tenantów na jednej małej instancji Postgres
- Łatwa migracja w przyszłości do schema-per-tenant, jeśli pojawi się klient z wymogami compliance (medyczne, bankowe)


---

## 2. TenantContext - serce systemu

Jedna, statyczna abstrakcja odpowiadająca na pytanie: **"kto teraz pyta?"**.

**Klasa:** `TenantContext`

**Odpowiedzialność:**
- Przechowuje `tenant_id` aktywnego requestu
- Udostępnia go *każdemu* miejscu w kodzie (repository, vector store, file storage, logger)
- Jest jedynym źródłem prawdy - nikt nie czyta tenant_id z JWT bezpośrednio, wszyscy chodzą przez TenantContext

**Mechanizm:**
- `ThreadLocal<Long>` - bo Spring używa modelu thread-per-request
- Statyczne metody: `set(tenantId)`, `get()`, `clear()`
- `get()` rzuca wyjątek jeśli kontekst nie ustawiony - lepiej crash niż cichy fallback do "default"

**Lifecycle:**
1. Filter na początku requestu: `TenantContext.set(...)`
2. Cały request korzysta - serwisy, repozytoria, Spring AI
3. Filter w `finally`: `TenantContext.clear()` - **krytyczne**, bo Spring używa thread poola i bez czyszczenia następny request mógłby odziedziczyć tenant_id

**Tryb MVP:**
- Filter zawsze ustawia `1L` bez patrzenia na request
- TenantContext API się nie zmienia - serwisy w ogóle nie wiedzą że to "MVP mode"

**Tryb produkcyjny:**
- Filter dekoduje JWT, wyciąga claim `tenant_id`, waliduje, ustawia
- Reszta kodu - bez zmian

---

## 3. Filter ustawiający TenantContext

**Klasa:** `TenantContextFilter extends OncePerRequestFilter`

**Odpowiedzialność:**
- Ustawić TenantContext na początku każdego requestu
- Wyczyścić w `finally` (nawet jeśli leci wyjątek)

**Tryby:**

| Tryb | Źródło tenant_id | Walidacja |
|------|------------------|-----------|
| **MVP** (`multibot.tenancy.mode=mock`) | Stała `1L` | brak |
| **JWT** (`multibot.tenancy.mode=jwt`) | Claim `tenant_id` w JWT | walidacja podpisu + istnienie tenanta w bazie |
| **Header** (`multibot.tenancy.mode=header`) | Nagłówek `X-Tenant-Id` | dla widgetu - tenant_id wyciągany z widget keya |

Tryb wybierany flagą w `application.properties`. Wszystkie 3 implementacje wstrzykiwane warunkowo (Spring `@ConditionalOnProperty`).

**Kolejność filtrów:**
1. CORS filter
2. **TenantContextFilter** (ten)
3. Spring Security filter (post-MVP)
4. Controller

TenantContext musi być ustawiony **przed** Spring Security, bo audyty bezpieczeństwa mogą się o niego opierać.

---

## 4. Hibernate `@TenantId` - automatyczne filtrowanie

**Po co:** żeby `SELECT * FROM documents WHERE id = ?` automatycznie stał się `SELECT * FROM documents WHERE id = ? AND tenant_id = ?`. Bez tego każdy programista musiałby pamiętać o filtrze - i prędzej czy później ktoś zapomni.

**Klasy:**
- `TenantIdResolver implements CurrentTenantIdentifierResolver<Long>` - mówi Hibernate jaki jest obecny tenant. Pobiera z `TenantContext.get()`.
- Każda encja JPA ma pole `@TenantId Long tenantId` (Hibernate 6 native annotation)

**Co dzieje się automatycznie:**

| Operacja | Co dodaje Hibernate |
|----------|---------------------|
| SELECT (`findById`, `findAll`, JPQL, criteria) | `WHERE tenant_id = :currentTenant` |
| INSERT (`save` nowej encji) | `tenantId` ustawiany przed insertem |
| UPDATE | warunek `WHERE tenant_id = :currentTenant` |
| DELETE | warunek `WHERE tenant_id = :currentTenant` |

**Czego NIE robi automatycznie:**
- Native SQL queries (`@Query(nativeQuery = true)`) - musisz dodać `WHERE tenant_id = :tenantId` ręcznie
- `JdbcTemplate` - całkowicie poza Hibernate, ręczna kontrola
- Spring AI VectorStore - to nie JPA, własny mechanizm filtrowania (patrz sekcja 5)

**Zasada:** jeśli czegoś nie da się załatwić przez JPA - osobna lista "uważaj" w [`backend.md`](backend.md).

---

## 5. pgvector - filtrowanie wektorów

Vector search w Spring AI nie wie nic o Hibernate `@TenantId`. To osobny świat - musimy filtrować świadomie.

**Schemat:** chunki w pgvector mają kolumnę `metadata` (JSONB) zawierającą m.in. `tenant_id`, `document_id`, `page_number`.

**Mechanizm filtrowania:**
- Spring AI udostępnia `SearchRequest` z polem `filterExpression`
- Każde wyszukiwanie buduje filter w stylu: `metadata['tenant_id'] == :currentTenantId`
- Klasa `TenantAwareVectorStore` (decorator nad Spring AI `VectorStore`) wymusza ten filtr przed delegacją do prawdziwego store

**Klucz:** developer **nigdy nie wywołuje** `VectorStore.search()` bezpośrednio. Zawsze przez `TenantAwareVectorStore`. To wymusza filtr na poziomie typu - nie da się zapomnieć.

Analogiczna walidacja przy zapisie chunka: `tenant_id` z TenantContext jest dopisywany do metadata przy każdym `add()`.

---

## 6. File storage - izolacja na poziomie ścieżek

**Klasa:** `FileStorageService` (interfejs) + `LocalFileStorage` (impl. MVP) / `S3FileStorage` (impl. produkcyjna)

**Konwencja ścieżek:**
```
./storage/
├── tenants/
│   ├── 1/
│   │   ├── documents/
│   │   │   ├── {document_uuid}.pdf
│   │   │   └── ...
│   ├── 2/
│   │   └── ...
```

**Walidacja:**
- `FileStorageService.read(documentId)` i `.write(documentId, bytes)` zawsze konstruuje ścieżkę z `TenantContext.get()`
- Brak metody pozwalającej podać `tenant_id` z zewnątrz - eliminuje błąd "podałem cudzy tenant"
- Dodatkowo: walidacja po stronie serwisu - dokument z DB ma `tenant_id == TenantContext.get()` przed otwarciem pliku

---

## 7. Belt-and-suspenders - walidacja w serwisach

Mimo automatycznego filtrowania Hibernate, każdy serwis manipulujący zasobem **ręcznie weryfikuje** że zasób należy do aktualnego tenanta. Defense in depth.

**Wzorzec:**
```
Document doc = documentRepository.findById(id).orElseThrow(NotFoundException::new);
TenantSecurityCheck.assertSameTenant(doc);  // rzuca ForbiddenException jeśli się nie zgadza
```

**Klasa:** `TenantSecurityCheck` - statyczne metody pomocnicze typu `assertSameTenant(TenantOwned entity)`.

**Interfejs:** `TenantOwned` - implementowany przez każdą encję która ma `tenant_id`. Metoda `getTenantId()`.

**Po co skoro Hibernate i tak filtruje:**
1. **Łapie błędy w native query** - jeśli ktoś napisze `@Query(nativeQuery=true)` bez filtru, Hibernate `@TenantId` nie pomoże, ale `assertSameTenant` tak
2. **Łapie błędy w JdbcTemplate** - jw.
3. **Defense in depth** - nawet jeśli ktoś zepsuje konfigurację Hibernate (np. wyłączy filter), serwisy nadal blokują dostęp
4. **Lepsze błędy** - `ForbiddenException` (403) zamiast `NotFoundException` (404) dla "to istnieje ale nie twoje" - choć z punktu widzenia bezpieczeństwa 404 jest preferowane (nie zdradza istnienia zasobu)

W praktyce: 404 dla user-facing endpointów, 500 + alert dla wewnętrznych - bo to bug.

---

## 8. Co się zmienia: MVP → multi-tenant

Cały sens designu: switch flagi, **nie refactor**.

| Element | MVP | Po włączeniu multi-tenant | Zmiana |
|---------|-----|---------------------------|--------|
| TenantContext API | `get() == 1L` zawsze | `get() == <z JWT>` | **żadna** w callerach |
| TenantContextFilter | hardcoded `set(1L)` | dekoduje JWT, ustawia | konfiguracja: `multibot.tenancy.mode=mock → jwt` |
| Spring Security | brak | włączone (`SecurityFilterChain`) | nowy bean, nowe filtry |
| Encje JPA | mają `@TenantId` | mają `@TenantId` | **żadna** |
| Repository | bez zmian | bez zmian | **żadna** |
| Vector store | `TenantAwareVectorStore` filtruje 1 | filtruje realny tenant | **żadna** w callerach |
| File storage | `./storage/tenants/1/...` | `./storage/tenants/{realny}/...` | **żadna** w callerach |
| Logi | `tenant_id=1` w MDC | `tenant_id=<realny>` w MDC | **żadna** |
| Tabela `tenants` | jeden rekord (id=1, name="default") | wiele rekordów | dane (Flyway seed) |
| Endpoint `/auth/login` | nie istnieje | dodajemy | nowy controller |
| Endpoint `/admin/tenants` | nie istnieje | dodajemy (super-admin) | nowy controller |

**Suma zmian przy przełączeniu:** 1 property, 1 filter (z mock na JWT), Spring Security beans, endpoint auth, endpoint admin. Cała reszta - 90%+ kodu - bez modyfikacji.

---

## 9. Zasady i pułapki

### Czerwone zasady (NIE łamać)

1. **Nigdy nie odczytuj `tenant_id` z requestu / JWT bezpośrednio.** Zawsze przez `TenantContext.get()`. Pojedyncze źródło prawdy.

2. **Nigdy nie pisz native SQL bez `WHERE tenant_id = ?`.** Hibernate `@TenantId` nie filtruje native queries.

3. **Nigdy nie używaj `VectorStore` bez `TenantAwareVectorStore`.** Spring AI VectorStore nie wie o tenantach.

4. **Nigdy nie konstruuj ścieżki do pliku z user input.** Tylko przez `FileStorageService`, który dokleja `tenant_id`.

5. **Nigdy nie zapominaj o `TenantContext.clear()` w `finally`.** Pozostały kontekst w thread poolu = wyciek tenant_id między requestami.

### Pułapki

- **`@Async` i background jobs** - nowy wątek = nowy ThreadLocal. Trzeba ręcznie propagować TenantContext do wątku workera. Klasa pomocnicza: `TenantAwareExecutor`.
- **Spring Events (`@EventListener`)** - synchroniczne dziedziczą wątek, asynchroniczne nie. Jak wyżej.
- **Scheduled tasks (`@Scheduled`)** - nie mają tenanta z definicji. Muszą same iterować po tenantach jeśli robią coś per-tenant.
- **WebSocket / SSE long-lived connections** - tenant ustawiany przy uchwyceniu connection, ale strumień odpowiedzi może żyć dłużej niż request. Wymaga osobnej logiki.
- **Testy** - testy integracyjne muszą ustawiać TenantContext ręcznie. Helper: `@WithTenant(1L)` adnotacja.

### Checklist dla nowego endpointu

Przed code review, autor sprawdza:
- [ ] Operuje tylko na encjach z `@TenantId`?
- [ ] Jeśli native query - ma `WHERE tenant_id = ?`?
- [ ] Jeśli vector search - przez `TenantAwareVectorStore`?
- [ ] Jeśli operacja na pliku - przez `FileStorageService`?
- [ ] Jest `TenantSecurityCheck.assertSameTenant(...)` w serwisie?
- [ ] Test sprawdza że tenant A nie widzi danych tenant B?

---

## 10. Lifecycle requestu - diagram

**Diagram sekwencji:** [`../diagrams/request-with-tenant-context.puml`](../diagrams/request-with-tenant-context.puml)

Pokazuje pełną ścieżkę requestu HTTP od klienta do bazy, z miejscami gdzie TenantContext jest ustawiany, odczytywany przez Hibernate, i czyszczony.

---

## Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Jak `tenant_id` wygląda w schemacie bazy | [`../database/schema.md`](../database/schema.md) |
| Konkretne klasy i pakiety implementujące powyższe | [`backend.md`](backend.md) |
| Jak filtrujemy pgvector technicznie | [`../database/pgvector.md`](../database/pgvector.md) |
