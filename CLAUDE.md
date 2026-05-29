# CLAUDE.md — Multibot (pełna wiedza dla agenta)

Multi-tenantowa platforma SaaS B2B: chatboty AI z bazą wiedzy (RAG) osadzane jako widget na stronach klientów.
Ten plik to onboarding dla agenta AI — przeczytaj go w całości zanim zaczniesz pracę.

> Język w repo i z właścicielem: **polski** (komunikaty commitów, UI, rozmowa). Kod/identyfikatory po angielsku.
> Właściciel nie lubi przegadanych odpowiedzi — pisz zwięźle, konkretnie.

---

## 1. Model domenowy (NAJWAŻNIEJSZE) — Organizacja → Boty

```
Organizacja (klient/konto)        <- plan, limit wiadomości (WSPÓLNY), max_bots, dane firmowe (NIP)
  ├── Bot A   (tenant_id, UUID)   <- granica izolacji danych: dokumenty, wektory, wersje, widget+klucz, sesje
  ├── Bot B   (tenant_id)
  └── Bot C   (tenant_id)
Użytkownik (OWNER/ADMIN)  -> należy do ORGANIZACJI (users.organization_id), zarządza wszystkimi jej botami
SUPER_ADMIN               -> ponad organizacjami (organization_id = NULL)
```

- **W kodzie „tenant" = BOT** (klasa `TenantId`, `@TenantId`, `TenantContext`, kolumna `tenant_id`). W UI/API klienta nazywamy to „bot".
- **Organizacja** trzyma plan, limit wiadomości i `max_bots`. **NIE** trzyma tego bot.
- **Limit wiadomości jest pooled** — sprawdzany jako SUMA `tenant_usage.user_messages` wszystkich botów organizacji vs limit planu.
- **Aktywny bot** trzymany w SESJI (atrybut `activeBotTenantId`). Owner przełącza go przełącznikiem; każda strona panelu pokazuje dane aktywnego bota.
- **Plany** (tabela `billing_plans`, edytowalne w DB): FREE/BASIC/PRO/ENTERPRISE. `max_bots`: Free 1, Basic 2, Pro 3, Enterprise ∞ (NULL). `monthly_messages_limit`: Free 500, Basic 2000, Pro 10000, Enterprise -1 (∞). Override per org: `organizations.monthly_messages_limit_override`, `organizations.max_bots_override`.
- Liczymy do limitu tylko wiadomości USERA (nie AI). Tokeny/koszty widzi tylko SUPER_ADMIN.

---

## 2. Stos technologiczny

| Warstwa | Tech |
|---|---|
| Backend | Java 21, Spring Boot 3.4.1, Spring Modulith 1.3.1 (moduły OPEN, hexagonalne), Spring AI 1.0.0 |
| Baza | PostgreSQL 16 + pgvector, Flyway (migracje per moduł) |
| AI | Anthropic `claude-haiku-4-5` (chat), OpenAI `text-embedding-3-small` (embeddingi) — przez Spring AI |
| Auth | Spring Security 6.4, **sesje cookie** (Spring Session JDBC, w Postgres), CSRF (double-submit), BCrypt. **NIE JWT.** |
| Frontend | React 19, Vite, TypeScript, Tailwind v4 (`@theme` w `src/index.css`), TanStack Query v5, react-router, openapi-fetch |
| Widget | Preact, bundle IIFE (`/widget/embed.js`), SSE |
| Rate limit | Bucket4j (in-memory) |
| Anti-bot | Cloudflare Turnstile (flaga, domyślnie off) |
| Deploy | Docker multi-stage, docker-compose, Caddy (auto-HTTPS Let's Encrypt), Hetzner |

Architektura backendu: **modular monolith**, każdy moduł hexagonalny (`domain/model`, `domain/port/in` (use cases), `domain/port/out` (repo), `application` (serwisy), `infrastructure` (adaptery), `api` (kontrolery)). Granice modułów pilnuje `ApplicationModulesTest` przez `allowedDependencies` w `package-info.java`.

---

## 3. Struktura repo

```
multibot/
├── CLAUDE.md                  # ten plik
├── docker-compose.yml         # lokalny Postgres+pgvector
├── deploy/                    # docker-compose.prod.yml, Dockerfile.backend, Dockerfile.web, Caddyfile, .env.example
├── docs/                      # dokumentacja (architecture/, api/, database/, flows/, diagrams/*.puml)
├── multibot-backend/          # Spring Boot
│   ├── .env                   # SEKRETY lokalne (gitignored): ANTHROPIC_API_KEY, OPENAI_API_KEY, DB_*, SPRING_PROFILES_ACTIVE
│   └── src/main/java/com/multibot/
│       ├── shared/            # TenantId, OrganizationId, wyjątki (ConflictException, NotFoundException, ValidationException...)
│       ├── tenancy/           # TenantContext, TenantContextFilter, resolvery (Mock/Authenticated), Hibernate @TenantId
│       ├── organization/      # klient platformy (ponad botami)
│       ├── tenant/            # = BOT (granica izolacji; ma organization_id)
│       ├── auth/              # login/sesje/użytkownicy, SecurityConfig, AuthController, AuthenticatedTenantResolver, bootstrap
│       ├── billing/           # plany, zużycie, limity (pooled + max_bots)
│       ├── security/          # tarcza widgetu: WidgetGuardFilter, RateLimiterService, TurnstileVerifier
│       ├── superadmin/        # provisioning klientów (org+owner+1.bot)
│       ├── bots/              # provisioning bota (AddBotUseCase) + self-serve OWNER (/api/bots)
│       ├── widget/, botversion/   # konfig+klucz widgetu; wersjonowanie bota (deep-copy)
│       ├── file/, vector/     # storage plików; pgvector + tenant filter
│       ├── document/, indexing/   # dokumenty; async pipeline indeksacji
│       └── chat/, rag/        # sesje+wiadomości (SSE); RAG orchestrator
├── multibot-frontend/         # React panel (Vite)
└── multibot-widget/           # Preact widget (embed.js)
```

---

## 4. API — konwencje i endpointy

Auth = **sesja (cookie `SESSION`) + CSRF (`XSRF-TOKEN` cookie ↔ nagłówek `X-XSRF-TOKEN`)**. Mutacje wymagają CSRF (poza `/api/widget/**`, `/api/chat/**`, `/actuator/**`).

**Wszystkie** kontrolery są **contract-first — generowane z `docs/api/openapi.yaml`** (build generuje interfejsy w `com.multibot.api.generated`, kontroler je implementuje; FE typy: `npm run gen:api` → `src/api/schema.ts`). Dotyczy też endpointów auth/bots/super-admin/billing/widget-origins/turnstile (domknięte 2026-05-29). Błędy walidacji/biznesowe lecą przez `GlobalExceptionHandler` → ProblemDetail (RFC 7807); 401 logowania = `BadCredentialsException`.

Endpointy spoza CRUD-owego rdzenia (warte zapamiętania):
- `POST /api/auth/login`, `/logout`, `GET /api/auth/me` (zwraca usera + organizationId + activeBot + bots[]), `POST /api/auth/active-bot` (przełącz aktywnego bota; waliduje że bot ∈ org usera)
- `GET /api/bots/limit`, `POST /api/bots` (self-serve OWNER; egzekwuje max_bots → 409)
- `GET/POST /api/super-admin/organizations`, `POST /api/super-admin/organizations/{id}/bots` (SUPER_ADMIN; omija limit botów)
- `GET /api/admin/billing/usage`, `/plan`
- `GET/PUT /api/admin/widget/origins` (dozwolone domeny aktywnego bota; URL→origin normalizacja)
- `GET /api/widget/turnstile`

Role/ścieżki (SecurityConfig): `/api/super-admin/**` → SUPER_ADMIN; `/api/bots/**` → OWNER; login/logout, `/api/widget/**`, `POST /api/chat/sessions[/...]`, `/actuator/health/**`, `/actuator/info` → publiczne; reszta → zalogowany. Limit wiadomości przekroczony → **402**; limit botów → **409**.

---

## 5. Lokalny development

### Baza
```bash
cd multibot && docker-compose up -d        # Postgres+pgvector na :5432 (db=multibot, user=multibot, pass=multibot)
```

### Backend
`.env` w `multibot-backend/` (gitignored) ma `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `DB_*`, `SPRING_PROFILES_ACTIVE=dev`.
```bash
cd multibot-backend
set -a && source .env && set +a && ./mvnw spring-boot:run
# ALBO (stabilniej, bez DevTools — patrz pułapka niżej):
set -a && source .env && set +a && SPRING_PROFILES_ACTIVE=dev java -jar target/multibot-backend-0.0.1-SNAPSHOT.jar
```
- Profil `dev`: `multibot.tenancy.mode=auth`, Swagger UI on, bootstrap kont.
- Backend na `:8080`. Health: `curl localhost:8080/actuator/health`.

### Frontend
```bash
cd multibot-frontend && npm install && npm run dev   # :5173, proxy /api i /actuator -> :8080
```
**Dev server (Vite) serwuje ze ŹRÓDŁA + HMR.** `npm run build` to paczka produkcyjna (`dist/`) — NIE zmienia tego co widać na :5173. Do klikania lokalnie wystarczy zapisać plik (HMR) + odświeżyć. `npm run build` używaj tylko do weryfikacji typów/przed deployem.

### Konta bootstrap (dev — z `application.yml`, nadpisywalne env)
- **Super admin:** `superadmin@multibot.local` / `Multibot!2026` (panel „Klienci")
- **Demo owner:** `owner@demo.local` / `Demo!Owner2026` (owner domyślnej org, 1 bot)

---

## 6. Testy

```bash
cd multibot-backend
./mvnw test            # SUREFIRE: testy jednostkowe + *Test (NIE *IT) + ApplicationModulesTest
./mvnw verify          # + FAILSAFE: integracyjne (*IT) na Testcontainers
./mvnw test -Dtest=OrgBotAuthFlowIT   # pojedynczy IT (surefire uruchomi *IT gdy podany jawnie)
```
- Profil `test`: `multibot.tenancy.mode=mock` (`MockTenantResolver` → DEFAULT tenant), mocki LLM/embeddingów (`MockLlmConfig`, `MockEmbeddingConfig`), Testcontainers Postgres (pgvector), `withReuse(true)`.
- **`IntegrationTestBase`** — baza ITów (RestAssured + Testcontainers).
- **`AuthenticatedIntegrationTestBase`** — dla ITów HTTP wymagających zalogowania (loguje się jako `owner@demo.local`, ustawia globalny RestAssured spec z cookie+CSRF). Stare ITy (Chat/Document/MultiTenant/Indexing) z niej dziedziczą.
- **`OrgBotAuthFlowIT`** (mode=auth via `@TestPropertySource`) — kluczowy IT modelu: login/me, super admin org+boty, przełączanie aktywnego bota, 403 obcy bot, pooled limit, self-serve 409, dozwolone domeny.
- Frontend: **Vitest + Testing Library + jsdom** (`npm test` / `npm run test:watch`; setup `src/test/setup.ts`, config w `vite.config.ts`). Pliki `*.test.ts(x)` kolokowane przy źródle. Pokrycie: utilki (`format`/`botColor`/`labels`/`api/errors`/`readCookie`) + krytyczny hook `useSwitchActiveBot` (cache przełączania bota — patrz §8.3). Brak testów E2E panelu — weryfikacja przez `npm run build` (typy) + ręcznie/curl.

Stan na teraz: `mvn verify` zielony (67 unit + 44 IT), `npm test` zielony (32), oba buildy czyste.

---

## 7. Serwer / Deploy (prod)

| | |
|---|---|
| Host | `root@178.105.248.132` (Hetzner, Ubuntu) |
| Katalog | `/opt/multibot` (git clone repo) |
| Domena | `https://multibot-test.duckdns.org` (DuckDNS) |
| Kontenery | `multibot-postgres`, `multibot-backend` (wewnętrzny, NIE wystawiony na host), `multibot-web` (Caddy, 80/443, auto-HTTPS) |
| Sekrety prod | `deploy/.env` na serwerze (gitignored): DOMAIN, DB_PASSWORD, klucze API, MULTIBOT_SUPERADMIN_*, MULTIBOT_TURNSTILE_* |

### Połączenie
```bash
ssh root@178.105.248.132
cd /opt/multibot
alias dc='docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env'
```

### Deploy (build NA serwerze z gita — nie pushujemy obrazów)
```bash
# lokalnie: commit + push na main (origin = git@github.com:pukala2/multi-bot.git)
git push origin main
# na serwerze:
cd /opt/multibot && git pull --ff-only origin main
dc up -d --build            # rebuild backend+web; backend na starcie odpala Flyway
dc up -d --build web        # tylko frontend (gdy zmiana tylko we froncie)
dc logs -f backend          # czekaj na "Started MultibotApplication"
```
- Weryfikacja: `curl https://multibot-test.duckdns.org/actuator/health` → 200. Backend nie jest na host:8080 — sprawdzaj przez domenę (Caddy) albo `docker exec`.
- **Migracje** uruchamiają się przy starcie backendu (Flyway). Przy zmianach schematu rób **backup** najpierw: `docker exec multibot-postgres pg_dump -U multibot multibot > /opt/multibot/backup_$(date +%F_%H%M).sql`.

### Provisioning klienta na prodzie
Przez panel super admina: „Klienci" → „Nowa organizacja" (nazwa, email właściciela, plan, opcj. nazwa+URL pierwszego bota). Modal pokazuje RAZ login+hasło+klucz widgeta. Kolejne boty: „Dodaj bota" (super admin omija limit) lub owner self-serve (do limitu planu).

### Embed widgetu (u klienta)
```html
<script src="https://multibot-test.duckdns.org/widget/embed.js"
  data-multibot-widget data-key="pk_widget_live_..."
  data-api-url="https://multibot-test.duckdns.org"></script>
```
Domeny widgetu (`allowed_origins`): owner ustawia na stronie Widget („Dozwolone domeny" — klocki z + i ×) albo przy dodawaniu bota (pole „Adres strony"). Pusto = działa wszędzie (tylko test).

---

## 8. Zasady i PUŁAPKI (uczone na błędach)

1. **Tenant z `TenantContext`, nigdy z żądania.** Native SQL/JdbcTemplate na danych tenantowych → ręcznie `WHERE tenant_id = ?`. Vector/pliki → przez właściwe abstrakcje. `TenantContext.clear()` w `finally`.
2. **Mutacje przez surowy `fetch`/`XMLHttpRequest` MUSZĄ ręcznie dodać `X-XSRF-TOKEN`** (middleware api clienta openapi-fetch łapie tylko wywołania `api.*`). Inaczej 403. (Bug uploadu: XHR dla paska postępu nie dodawał tokenu.)
3. **Przełączenie aktywnego bota:** `useSwitchActiveBot` robi `qc.removeQueries` (poza `/me`) + `AppShell` remountuje stronę (`<Outlet key={activeBot.id}>`). NIE inicjalizuj lokalnego stanu (formularzy) ze stale cache — inaczej edycja/zapis trafia do złego bota.
4. **DevTools restart-loop:** backend z `spring-boot:run` ma DevTools; jeśli w międzyczasie odpalisz `mvn verify`/`test` na tym samym module, przekompiluje `target/classes` i DevTools restartuje backend w pętli → requesty padają. Lokalnie do „postawienia żeby klikać" używaj `java -jar` (jar nie ma DevTools).
5. **`mvn test` ≠ `mvn verify`** — ITy (`*IT`) idą przez failsafe (`verify`). Sam `test` ich nie uruchomi.
6. **Build na serwerze:** Docker Hub bywa flaky (`auth.docker.io ... 404` przy pobieraniu obrazu bazowego) — po prostu ponów `dc up -d --build`.
7. **Migracje:** baseline-on-migrate on; brak `IF NOT EXISTS`; per-moduł foldery w `db/migration/<moduł>/`, zarejestrowane w `application.yml` (`spring.flyway.locations`). Wersje: `V<data>_<nnnn>__opis.sql`. Migracja zmieniająca schemat tenants/users/billing musi mieć backfill.
8. **`Optional` tylko jako typ zwracany metody, nie pole** (User/MultibotUserDetails mają nullable pola — bo trafiają do serializacji sesji; Optional nie jest Serializable).
9. **Plany w DB, nie w configu** — super admin (docelowo) edytuje. Liczby (max_bots, limity) zmieniasz w seedzie/migracji billing.
10. **Sekrety:** `.env` (backend) i `deploy/.env` są gitignored — NIGDY nie commituj. `storage/`, `target/`, `node_modules/`, `dist/` też gitignored.
11. Shell właściciela wypisuje `Generating CODEARTIFACT_AUTH_TOKEN` przed każdą komendą — to artefakt profilu (firmowy laptop), ignoruj. CodeArtifact NIE jest używany na serwerze (Maven Central działa).
12. **Contract-first a sesje/principal:** generowane interfejsy `*Api` mają TYLKO parametry z openapi — NIE dostaniesz `HttpServletRequest/Response`, `Authentication`, `@AuthenticationPrincipal` jako argumentu metody. Pobieraj je inaczej: principal/auth z `SecurityContextHolder`, request przez wstrzyknięty (request-scoped) `HttpServletRequest`. `AuthController` zapisuje kontekst bezpieczeństwa do atrybutu sesji (`SPRING_SECURITY_CONTEXT_KEY`) zamiast `SecurityContextRepository.saveContext(req,resp)` — bo `HttpServletResponse` nie jest dostępny. Dodając ręczny endpoint do `openapi.yaml` dodaj `"api"` do `allowedDependencies` modułu (inaczej `ApplicationModulesTest` czerwony).

---

## 9. Jak pracujemy (workflow z właścicielem)

- **Najpierw plan, potem implementacja** — przy większych zmianach ustal plan działania, potem koduj.
- Po zmianach: **kompiluj** (`./mvnw test-compile` łapie też błędy testów jak na serwerze; front `npm run build`) i **weryfikuj** zanim powiesz „zrobione". Najlepiej smoke test (curl/uruchomienie).
- Commity: zwięzłe, po polsku, grupy logiczne, zakończone `Co-Authored-By: Claude ...`. Push na `main`. Deploy = pull+rebuild na serwerze.
- Właściciel testuje klikając w panelu (lokalnie i na prodzie) — bądź gotów szybko diagnozować z logów (`docker logs multibot-backend`, log lokalny) i reprodukować curl-em.
- Dług techniczny nazywaj wprost; nie udawaj że coś jest przetestowane jeśli nie jest.

---

## 10. Stan i backlog (na czas pisania)

**Zrobione i na prodzie:** pivot Organizacja→Boty, auth (sesje), billing pooled + max_bots, super admin (org+boty), self-serve dodawania botów, przełącznik botów, dozwolone domeny widgetu (klocki) + URL przy dodawaniu bota, `mvn verify` zielony, dokumentacja tekstowa zaktualizowana.

**Backlog / niezrobione:**
- Impersonacja: super admin „wejdź jako klient" (resolver ma już gałąź `impersonatedTenantId`, brak endpointu/UI).
- Użytkownicy w organizacji: OWNER zaprasza ADMINów, wymuszona zmiana hasła (status `MUST_CHANGE_PASSWORD` istnieje).
- Turnstile: realne klucze + włączenie (`MULTIBOT_TURNSTILE_*` w prod .env), gdy domena w Cloudflare.
- Diagramy `docs/diagrams/*.puml` — nieaktualne (stary model tenant=Long/JWT) — do uzupełnienia. (openapi.yaml domknięty: zawiera już wszystkie endpointy, w tym auth/bots/super-admin/billing/widget-origins/turnstile.)
- Alerty mailowe 80%/100% limitu, dedup plików po hashu, RODO, samodzielna rejestracja, płatności.

---

## 11. Kluczowe pliki do przeczytania na start

- `docs/architecture/multi-tenancy.md` — serce modelu (TenantContext, resolver, izolacja, pooled limit).
- `docs/database/schema.md` — tabele (organizations, tenants, users, billing).
- `docs/api/endpoints.md` — wszystkie endpointy + auth.
- `multibot-backend/src/main/java/com/multibot/auth/infrastructure/security/{SecurityConfig,AuthenticatedTenantResolver}.java` — auth + aktywny bot.
- `multibot-backend/src/test/java/com/multibot/e2e/OrgBotAuthFlowIT.java` — jak testować flow auth/bot.
