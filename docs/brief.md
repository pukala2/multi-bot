# Projekt: Platforma Chatbotów AI z RAG dla firm (B2B SaaS)

## 1. Wizja i cel biznesowy

### Co chcę zbudować
Platforma SaaS w modelu B2B, która umożliwia firmom posiadanie własnego chatbota AI odpowiadającego na pytania na podstawie ich dokumentów wewnętrznych. Każdy klient (firma) loguje się do platformy, wgrywa swoje pliki (regulaminy, FAQ, instrukcje, cenniki, procedury), a chatbot odpowiada użytkownikom końcowym wyłącznie na podstawie tych dokumentów (RAG - Retrieval Augmented Generation).

### Dla kogo
- Średnie firmy (50-500 pracowników) posiadające infolinię lub support tier 1
- Branże potencjalne: e-commerce, SaaS B2B, edukacja, multiagencje ubezpieczeniowe, kancelarie doradcze
- Klient docelowy: firma która chce odciążyć obsługę klienta i odpowiadać 24/7 na powtarzalne pytania

### Problem do rozwiązania
Firmy tracą czas i pieniądze na odpowiadanie na te same pytania od klientów. Pracownicy obsługi powtarzają w kółko te same odpowiedzi zamiast zajmować się trudniejszymi sprawami. Klienci czekają w kolejce na infolinii zamiast dostać natychmiastową odpowiedź.

### Model biznesowy
Subskrypcja miesięczna per firma-klient. Plany różniące się limitem dokumentów, liczbą zapytań, liczbą użytkowników końcowych, ewentualnie modelem LLM (Haiku vs Sonnet/GPT-4).

---

## 2. Zakres funkcjonalny MVP

### Co MUSI być w MVP

**Panel klienta (firmy):**
- Logowanie i rejestracja użytkowników (administratorów po stronie klienta)
- Panel zarządzania dokumentami:
  - Upload plików (PDF, DOCX, TXT, MD)
  - Lista wgranych dokumentów z metadanymi (nazwa, rozmiar, data uploadu, status przetwarzania)
  - Usuwanie dokumentów
  - Status indeksacji (pending / processing / ready / failed)
- Okno chatu testowego (do testowania bota przez admina przed udostępnieniem)
- Historia rozmów (przeglądanie konwersacji użytkowników końcowych z botem)
- Podstawowe statystyki (liczba rozmów, najczęstsze pytania)

**Bot (interfejs dla użytkownika końcowego):**
- Okno chatu osadzane na stronie klienta (widget) lub dostępne pod dedykowanym linkiem
- Streaming odpowiedzi (tekst pojawia się stopniowo)
- Pamięć kontekstu rozmowy (bot pamięta wcześniejsze wiadomości w tej samej sesji)
- Cytowanie źródeł (bot pokazuje z jakiego dokumentu pochodzi odpowiedź)
- Sesje rozmów per użytkownik końcowy

**Backend:**
- Przetwarzanie plików: ekstrakcja tekstu → chunking → generowanie embeddingów → zapis do vector store
- Endpoint chatu z RAG: query → embedding → wyszukiwanie kontekstu → wywołanie LLM → odpowiedź
- Multi-tenancy: izolacja danych między firmami-klientami
- Autoryzacja i autentykacja
- Zapis historii rozmów

### Czego NIE robimy w MVP (świadomie odkładamy)
- Billing i automatyczne plany subskrypcyjne (na start - manualne)
- White-label (customowy branding, własna domena klienta)
- Integracje z CRM/ticketing
- Eskalacja do żywego człowieka
- Wielojęzyczność (start: polski)
- Aplikacja mobilna
- Voice (rozmowa głosowa)
- Zaawansowany RAG (rerankery, hybrid search, agenty)
- Analityka zaawansowana

---

## 3. Stack technologiczny

### Backend
- **Java 21 + Spring Boot 3.x**
- **Spring AI** - integracja z LLM i vector store
- **Spring Security + JWT** - autentykacja
- **Spring Data JPA + Hibernate 6** - ORM, wsparcie dla `@TenantId` (multi-tenancy)
- **PostgreSQL 16 + pgvector** - jedna baza na dane relacyjne i wektory
- **Flyway** - migracje bazy
- **Apache Tika** - ekstrakcja tekstu z różnych formatów plików

### Frontend
- **React 18 + TypeScript + Vite**
- **shadcn/ui + Tailwind CSS** - komponenty UI
- **TanStack Query** - zarządzanie stanem serwera i cache
- **React Router** - routing
- **Axios / Fetch** - komunikacja z API

### LLM i embeddingi
- **Anthropic Claude (Haiku/Sonnet)** lub **OpenAI (gpt-4o-mini/gpt-4o)** - wybór per plan klienta
- **OpenAI text-embedding-3-small** - embeddingi (tani, dobry)
- Konfigurowalne przez Spring AI - możliwość zmiany providera

### Infrastruktura (rozwojowa - lokalnie)
- **Docker Compose** - cały stack lokalnie (Postgres, backend, frontend)
- **Maven** - build backendu
- **npm/pnpm** - build frontendu

### Infrastruktura (produkcyjna - później)
- VPS Hetzner (start: CPX21 ~35 zł/mc)
- Caddy/Traefik - reverse proxy z automatycznym HTTPS
- Backup do Hetzner Storage Box

---

## 4. Architektura - kluczowe decyzje

### Multi-tenancy: Shared database, shared schema
- **Jedna instancja aplikacji** obsługuje wszystkich klientów
- **Jedna baza PostgreSQL** - każda tabela ma kolumnę `tenant_id`
- **Izolacja na poziomie aplikacji** - Hibernate `@TenantId` automatycznie filtruje zapytania
- **Tenant resolution: po JWT** - tenant_id zaszyty w tokenie po zalogowaniu
- **Vector store** - pgvector w tej samej bazie, filtrowanie po `tenant_id` w metadanych chunków

Uzasadnienie: prostsze utrzymanie, jeden deploy, jedna baza do backupu, łatwa skalowalność do dziesiątek klientów. Pełna izolacja danych przez systematyczne użycie tenant_id we wszystkich query.

### RAG flow

**Upload i indeksacja (asynchroniczne):**
1. Klient wgrywa plik przez panel
2. Backend zapisuje plik do storage (lokalnie / S3)
3. Tworzy rekord `document` ze statusem `pending`
4. Job w tle (np. `@Async` lub kolejka):
   - Ekstrakcja tekstu (Apache Tika)
   - Chunking (Spring AI TokenTextSplitter, ~500 tokenów per chunk z overlap)
   - Generowanie embeddingów dla każdego chunka
   - Zapis chunków do pgvector z metadanymi (tenant_id, document_id, filename, page)
   - Update statusu dokumentu na `ready`

**Chat z RAG:**
1. Użytkownik wysyła pytanie
2. Backend generuje embedding pytania
3. Wyszukiwanie top-K (np. 5) najbardziej podobnych chunków w pgvector (filtr: tenant_id)
4. Budowa promptu: system prompt + kontekst z chunków + historia ostatnich N wiadomości + pytanie
5. Wywołanie LLM (streaming)
6. Strumieniowanie odpowiedzi do frontendu (Server-Sent Events)
7. Zapis wiadomości użytkownika i odpowiedzi do bazy
8. Zwrot źródeł (które chunki zostały użyte) do wyświetlenia w UI

### Pamięć kontekstu rozmowy
- Per sesja czatu (sessionId)
- Doklejanie ostatnich N wiadomości (np. 10) do promptu LLM
- Limit tokenów - jeśli historia za długa, podsumowanie starszych wiadomości (do rozważenia w v2)

### Security i izolacja
- JWT z tenant_id i user_id
- Filter w Spring który dla każdego requestu ustawia TenantContext
- Hibernate `@TenantId` na każdej encji - automatyczne filtrowanie
- Vector search ZAWSZE z filtrem tenant_id
- Walidacja po stronie API: user może operować tylko na zasobach swojego tenanta

---

## 5. Model domenowy (główne encje)

### Tenant (firma-klient)
- id, name, subdomain (opcjonalnie), plan, created_at, status

### User (użytkownik panelu - pracownik firmy-klienta)
- id, tenant_id, email, password_hash, role (admin/user), created_at

### Document (wgrany plik)
- id, tenant_id, filename, file_path, file_size, mime_type, status (pending/processing/ready/failed), uploaded_by (user_id), uploaded_at, indexed_at

### Chunk (fragment dokumentu w vector store)
- id, tenant_id, document_id, chunk_index, content (text), embedding (vector), metadata (json - np. page number)

### EndUser (użytkownik końcowy rozmawiający z botem)
- id, tenant_id, external_id (opcjonalne - jeśli klient identyfikuje swoich userów), created_at
- Uwaga: może być anonymous - identyfikacja przez session

### ChatSession (sesja rozmowy)
- id, tenant_id, end_user_id (lub anonymous_id), started_at, last_activity_at

### Message (pojedyncza wiadomość)
- id, tenant_id, session_id, role (user/assistant), content, sources (json - lista chunk_id), created_at, tokens_used

---

## 6. Główne flow'y (do narysowania jako diagramy sekwencji)

1. **Rejestracja i logowanie użytkownika panelu** (z generowaniem JWT z tenant_id)
2. **Upload i indeksacja dokumentu** (synchroniczna część + asynchroniczna)
3. **Zadanie pytania przez użytkownika końcowego** (RAG + streaming odpowiedzi)
4. **Usunięcie dokumentu** (kaskadowe usunięcie chunków z vector store)
5. **Onboarding nowego tenanta** (utworzenie tenanta + pierwszego admina)

---

## 7. Endpointy API (zarys, do uszczegółowienia)

### Auth
- POST `/api/auth/register` - rejestracja nowego tenanta + admin user
- POST `/api/auth/login` - logowanie, zwrot JWT
- POST `/api/auth/refresh` - odświeżenie tokenu

### Documents (panel klienta)
- GET `/api/documents` - lista dokumentów tenanta
- POST `/api/documents` - upload nowego dokumentu
- DELETE `/api/documents/{id}` - usunięcie dokumentu
- GET `/api/documents/{id}/status` - status indeksacji

### Chat (panel + widget)
- POST `/api/chat/sessions` - utworzenie nowej sesji
- GET `/api/chat/sessions` - lista sesji (dla admina - do przeglądania historii)
- GET `/api/chat/sessions/{id}/messages` - historia wiadomości w sesji
- POST `/api/chat/sessions/{id}/messages` - wysłanie wiadomości (streaming response - SSE)

### Admin (panel klienta)
- GET `/api/admin/users` - lista użytkowników w tenant
- POST `/api/admin/users` - dodanie użytkownika
- GET `/api/admin/stats` - podstawowe statystyki

### Public widget (dla użytkowników końcowych)
- POST `/api/widget/sessions` - utworzenie anonimowej sesji (na podstawie tenant_id z widget tokena)
- POST `/api/widget/sessions/{id}/messages` - wysłanie wiadomości

---

## 8. Co chcę osiągnąć w pierwszej iteracji (lokalnie)

### Cel
Działająca aplikacja lokalnie na moim komputerze, z którą mogę:
1. Zarejestrować nowego tenanta i admina
2. Zalogować się do panelu
3. Wgrać kilka PDF-ów / DOCX-ów
4. Zobaczyć że pliki są zaindeksowane
5. Otworzyć chat i zadać pytanie - dostać sensowną odpowiedź opartą o wgrane dokumenty
6. Zobaczyć źródła odpowiedzi
7. Sprawdzić że pamięta kontekst (mogę zadać follow-up question)
8. Stworzyć drugiego tenanta i sprawdzić że dane są izolowane

### Setup lokalny - jak ma działać
- `docker-compose up` uruchamia PostgreSQL z pgvector
- Backend uruchamiany przez Maven / IDE (Spring Boot)
- Frontend uruchamiany przez `npm run dev` (Vite dev server)
- Zmienne środowiskowe w `.env` (klucze API do OpenAI/Anthropic)
- Flyway automatycznie tworzy schemat bazy przy starcie backendu

---

## 9. Dokumenty które chcę przygotować (z Claude Code)

1. **Projekt bazy danych** - pełny schemat z relacjami, indeksami, ograniczeniami; migracje Flyway
2. **Architektura systemu** - high-level diagram (frontend ↔ backend ↔ DB ↔ LLM API), warstwy, komponenty
3. **Architektura backendu** - struktura pakietów, warstwy (controller/service/repository), kluczowe komponenty (TenantContext, RAG service, embedding service, document processor)
4. **Diagramy sekwencji** - dla głównych flow'ów z sekcji 6
5. **Specyfikacja API** - szczegółowa, z przykładami request/response (najlepiej OpenAPI/Swagger)
6. **Flow RAG** - szczegółowy diagram procesowania dokumentów i odpowiadania na pytania
7. **Multi-tenancy & security** - jak konkretnie zapewniam izolację (TenantContext, Hibernate filters, vector store filters)
8. **ADR (Architecture Decision Records)** - krótkie notki uzasadniające kluczowe wybory technologiczne
9. **README / Setup guide** - jak odpalić projekt lokalnie krok po kroku
10. **Backlog / roadmap** - co po MVP (multi-tenancy refinements, widget embed, billing, integracje, etc.)

---

## 10. Założenia i ryzyka

### Założenia
- Jestem dev backendowy (Java/Spring) i jestem komfortowy w tym stacku
- Frontend - jestem w stanie ogarnąć React, używam shadcn/ui żeby przyspieszyć
- Mam kolegę od sprzedaży - on szuka klientów równolegle
- Pracuję full-time nad projektem
- Mam dostęp do API OpenAI / Anthropic (klucze)

### Ryzyka techniczne
- Jakość odpowiedzi RAG zależy od jakości dokumentów klienta - trzeba przygotować się na tuning chunkingu, promptów, ewentualnie reranker
- Halucynacje LLM - musi być jasno powiedziane w promptcie "odpowiadaj TYLKO na podstawie kontekstu, jeśli nie wiesz - powiedz że nie wiesz"
- Koszty LLM API mogą być nieprzewidywalne przy dużej liczbie zapytań - trzeba mieć monitoring i limity per tenant

### Ryzyka biznesowe
- Rynek konkurencyjny (Intercom Fin, Ada, lokalne firmy konsultingowe AI)
- Sprzedaż B2B = długi cykl (3-12 miesięcy)
- Brak case studies na start - trudniej przekonać klienta
- Trudne domeny (prawo, medycyna, finanse) = wysokie wymagania jakościowe i compliance

### Strategia mitygacji
- Wybór niszy z mniejszymi wymaganiami regulacyjnymi na start (e-commerce, edukacja, SaaS B2B)
- Płatne PoC zamiast od razu produkcji
- Bardzo jasne komunikowanie ograniczeń bota klientowi
- Cytowanie źródeł - klient widzi skąd odpowiedź, zaufanie rośnie

---

## 11. Kontekst do dalszej pracy z Claude Code

Ten dokument to **brief biznesowo-techniczny**. Następne kroki w nowej sesji z Claude Code:
1. Wrzucenie tego dokumentu jako kontekst
2. Wspólne projektowanie szczegółowej architektury (z diagramami w Mermaid)
3. Schemat bazy danych - SQL + migracje Flyway
4. Struktura projektu Spring Boot (pom.xml, pakiety, podstawowe klasy)
5. Implementacja iteracyjna: auth → multi-tenancy → documents → RAG → chat → frontend
6. Sukcesywne dodawanie plików do repo i commitowanie postępów
