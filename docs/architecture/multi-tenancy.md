# Multi-tenancy — model Organizacja → Boty i izolacja danych

Najważniejszy dokument architektoniczny. Decyduje jak wygląda reszta kodu.

> **Aktualizacja:** dokument opisuje model **po pivocie Organizacja→Boty** (oraz po dodaniu auth/billingu).
> Wcześniejsze wersje zakładały płaski model „tenant = klient" i JWT — to już nieaktualne.

---

## 1. Dwa poziomy: Organizacja i Bot (tenant)

```
Organizacja (klient, konto)           <- plan, limit wiadomości, dane firmowe (NIP)
  ├── Bot A  (tenant_id)              <- granica izolacji danych: dokumenty, wektory, sesje, widget
  ├── Bot B  (tenant_id)
  └── Bot C  (tenant_id)
Użytkownik (OWNER/ADMIN)  -> należy do ORGANIZACJI (nie do pojedynczego bota)
SUPER_ADMIN              -> ponad organizacjami (bez organizacji)
```

- **Organizacja** = konto klienta. Trzyma **plan** (FK `billing_plans`), **limit wiadomości** (wspólny dla wszystkich botów), **limit liczby botów** (`max_bots`), dane firmowe.
- **Bot = tenant** = granica izolacji danych. Każdy bot ma własne dokumenty, chunki (wektory), wersje, widget+klucz, sesje czatu, licznik zużycia. Wszystkie tabele danych mają kolumnę `tenant_id` = id bota.
- **Użytkownik** należy do organizacji (`users.organization_id`), więc jeden owner zarządza wszystkimi botami swojej organizacji. SUPER_ADMIN nie ma organizacji.

> Wewnętrznie w kodzie nadal mówimy „tenant" (klasa `TenantId`, `@TenantId`, `TenantContext`) — to po prostu **id bota**. W UI/API klienta nazywamy to „bot".

**Strategia izolacji: Pool model.** Jedna baza, jeden schemat, kolumna `tenant_id` na każdej tabeli danych. Izolacja przez systematyczne filtrowanie po `tenant_id`. Tanie i wystarczające na pierwsze setki klientów; łatwa późniejsza migracja do schema-per-tenant.

---

## 2. TenantContext — serce systemu

**Klasa:** `com.multibot.tenancy.TenantContext`

- Przechowuje `TenantId` (UUID) **aktywnego bota** dla bieżącego requestu w `ThreadLocal`.
- API: `set(TenantId)`, `get()` (rzuca `IllegalStateException` gdy nie ustawiony — lepiej crash niż cichy fallback), `tryGet()` (Optional), `clear()`.
- Jedyne źródło prawdy — repozytoria, vector store, file storage, logger chodzą przez `TenantContext`, nikt nie czyta tenanta z sesji/żądania bezpośrednio.

**Lifecycle:** `TenantContextFilter` ustawia kontekst na początku requestu i **czyści w `finally`** (krytyczne — Spring używa thread-poola; bez `clear()` następny request odziedziczyłby tenant).

---

## 3. Rozwiązywanie aktywnego bota — TenantResolver

`TenantContextFilter` deleguje do `TenantResolver` (interfejs). Tryb wybierany flagą `multibot.tenancy.mode`:

| Tryb | Implementacja (`@ConditionalOnProperty`) | Źródło tenanta |
|------|------------------------------------------|----------------|
| `mock` (domyślny, też testy) | `MockTenantResolver` | stały DEFAULT tenant `00000000-…-0001` |
| `auth` (produkcja) | `AuthenticatedTenantResolver` | aktywny bot z sesji (kolejność niżej) |

**`AuthenticatedTenantResolver` — kolejność:**
1. **Impersonacja** — atrybut sesji `impersonatedTenantId` (super admin „wejdź jako klient", planowane).
2. **Aktywny bot** — atrybut sesji `activeBotTenantId`, ustawiany przy **loginie** (auto-wybór pierwszego bota organizacji) i przy **przełączniku** (`POST /api/auth/active-bot`, po walidacji że bot należy do organizacji usera).
3. **Fallback** — zalogowany owner bez wybranego bota (np. sesja sprzed wdrożenia) → auto-wybór pierwszego bota org i zapis w sesji.
4. **Widget (anonim)** — `WidgetGuardFilter` po walidacji `X-Multibot-Widget-Key` + Origin + rate-limit wrzuca `tenant_id` w atrybut requestu; resolver go odczytuje.
5. Brak → `TenantContext.get()` rzuci błędem (zamierzone — np. SUPER_ADMIN na endpointcie klienta).

> **Auth = sesje (cookie), NIE JWT.** Spring Security 6.4 + Spring Session JDBC (sesje w Postgres). Login `POST /api/auth/login`, logout, `GET /api/auth/me` (zwraca usera + `organizationId` + `activeBot` + listę `bots`). CSRF: `XSRF-TOKEN` cookie + nagłówek `X-XSRF-TOKEN`.

**Kolejność filtrów (tryb auth):** filtry tenancy są wpięte **wewnątrz** łańcucha Spring Security (po autoryzacji), bo resolver czyta `SecurityContext`:
```
… Spring Security (auth + AuthorizationFilter) → WidgetGuardFilter → TenantContextFilter → Controller
```
(`TenantContextFilter` ma wyłączoną auto-rejestrację jako globalny servlet filter — `FilterRegistrationBean(enabled=false)` — i jest dodawany do chaina przez `addFilterAfter`.)

---

## 4. Hibernate `@TenantId` — automatyczne filtrowanie

Żeby `findById` automatycznie stał się `… AND tenant_id = ?`. Bez tego ktoś prędzej czy później zapomni o filtrze.

- Każda encja danych ma pole `@TenantId UUID tenantId` (Hibernate 6 native).
- `TenantIdResolver implements CurrentTenantIdentifierResolver<UUID>` — pobiera z `TenantContext.get()`.
- Konfiguracja: `HibernateTenancyCustomizer`.

| Operacja | Co dodaje Hibernate |
|----------|---------------------|
| SELECT / UPDATE / DELETE | `WHERE tenant_id = :current` |
| INSERT | `tenant_id` ustawiany przed insertem |

**Nie obejmuje automatycznie:** native SQL (`@Query(nativeQuery=true)`), `JdbcTemplate`, pgvector — tam filtrujemy ręcznie.

---

## 5. pgvector — filtrowanie wektorów

Chunki mają kolumnę `tenant_id`. Wyszukiwanie wektorowe filtruje po `tenant_id` z `TenantContext` (poza Hibernate, świadomie). Zapis chunka dopisuje `tenant_id` aktywnego bota. Szczegóły: [`../database/pgvector.md`](../database/pgvector.md).

---

## 6. File storage — izolacja po ścieżkach

```
./storage/tenants/{tenant_id}/files/{file_uuid}.<ext>
```
`FileStorageService` konstruuje ścieżkę z `TenantContext.get()` — brak metody z `tenant_id` z zewnątrz. `tenant_id` = id bota.

---

## 7. Billing — limit wiadomości wspólny (pooled) na organizację

- Plan i `monthly_messages_limit` (+ override) są na **organizacji**, nie na bocie.
- Zużycie liczone **per bot** (`tenant_usage`) — dla analityki, ale **limit sprawdzany jest jako SUMA wiadomości wszystkich botów organizacji** vs limit planu.
- `BillingService.check(tenantId)` rozwiązuje organizację bota i sumuje zużycie po wszystkich jego botach (`sumUserMessagesForOrgOf`). Po przekroczeniu — `402 PAYMENT_REQUIRED`.
- Limit liczby botów (`max_bots`: Free 1 / Basic 2 / Pro 3 / Enterprise ∞ lub override) — egzekwowany przy self-serve `POST /api/bots`.

---

## 8. Anonimowy widget — bezpieczeństwo

Endpointy czatu (`POST /api/chat/sessions[/…/messages]`) są `permitAll`, ale chroni je `WidgetGuardFilter`:
- request zalogowanego usera (test czatu w panelu) → przepuszczany;
- anonimowy widget → wymaga `X-Multibot-Widget-Key` + dozwolony Origin, rate-limit per IP/sesja, opcjonalnie Cloudflare Turnstile; po walidacji wrzuca `tenant_id` w atrybut requestu dla resolvera.

---

## 9. Czerwone zasady (NIE łamać)

1. Nigdy nie czytaj tenanta z sesji/żądania bezpośrednio — zawsze `TenantContext.get()`.
2. Native SQL / `JdbcTemplate` na danych tenantowych — zawsze `WHERE tenant_id = ?` ręcznie.
3. Vector search / zapis chunków — zawsze z filtrem `tenant_id`.
4. Ścieżki plików — tylko przez `FileStorageService`.
5. Zawsze `TenantContext.clear()` w `finally`.
6. Plan/limit/dane firmowe to poziom **organizacji** — nie duplikuj ich na bocie.

### Pułapki
- `@Async` / background / Spring Events asynchroniczne — nowy wątek = nowy ThreadLocal; propaguj `TenantContext` (`TenantAwareTaskDecorator`).
- SSE czatu — strumień żyje dłużej niż request; tenant ustawiany przy starcie.
- Testy — w profilu `test` (`mode=mock`) resolver zwraca DEFAULT tenant; ITy HTTP wymagające auth logują się przez `AuthenticatedIntegrationTestBase`. Testy DB ustawiają `TenantContext` ręcznie.

### Checklist nowego endpointu klienta
- [ ] Operuje na encjach z `@TenantId` (albo native query z `WHERE tenant_id = ?`)?
- [ ] Vector / plik — przez właściwe abstrakcje?
- [ ] Test sprawdza izolację (bot A nie widzi danych bota B)?
- [ ] Jeśli endpoint zależny od bota — czy SUPER_ADMIN (bez aktywnego bota) jest poprawnie obsłużony / ukryty w UI?

---

## Linki dalej

| Chcesz wiedzieć… | Idź do… |
|--------------------|-----------|
| Schemat bazy (organizations, tenants, users, billing) | [`../database/schema.md`](../database/schema.md) |
| Lista endpointów (auth, bots, super-admin, billing) | [`../api/endpoints.md`](../api/endpoints.md) |
| Moduły backendu | [`backend/modules-business.md`](backend/modules-business.md) |
| Filtrowanie pgvector | [`../database/pgvector.md`](../database/pgvector.md) |
