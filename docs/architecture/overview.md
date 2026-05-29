# Architektura - widok ogólny

Wysokopoziomowy obraz systemu Multibot: co jest w środku, co poza, jak komponenty się komunikują.
Szczegóły implementacyjne (struktura pakietów, klasy, kod) → [backend.md](backend.md).
Szczegóły izolacji tenantów → [multi-tenancy.md](multi-tenancy.md).

---

## 1. Słownik pojęć

> **Aktualizacja (pivot Organizacja→Boty):** „Tenant" znaczy teraz **bot** (granica izolacji danych), a klientem
> platformy jest **Organizacja** (może mieć wiele botów). Pełny model: [multi-tenancy.md](multi-tenancy.md).

| Pojęcie | Znaczenie |
|---------|-----------|
| **Organizacja** | Firma-klient platformy (konto). Trzyma plan, limit wiadomości (wspólny dla botów), dane firmowe. Może mieć wiele botów. |
| **Tenant / Bot** | Pojedynczy chatbot ze swoimi dokumentami, widgetem, sesjami. Granica izolacji danych (`tenant_id`). Należy do organizacji. |
| **Admin / Owner** | Użytkownik panelu po stronie klienta (należy do organizacji). Loguje się, zarządza botami, wgrywa pliki. `SUPER_ADMIN` zarządza całą platformą. |
| **End user** | Użytkownik końcowy, który rozmawia z botem - klient firmy-Tenanta. Anonimowy lub identyfikowany przez `external_id`. |
| **Document** | Plik wgrany przez Admina (PDF, DOCX, TXT, MD). Po przetworzeniu staje się źródłem wiedzy dla bota. |
| **Chunk** | Fragment dokumentu (~500 tokenów) z wygenerowanym embeddingiem, zapisany w vector store. |
| **Chat session** | Pojedyncza rozmowa End usera z botem. Ma swoją historię wiadomości i utrzymuje kontekst. |
| **Widget** | Kawałek JavaScriptu osadzany na stronie Tenanta, przez który End user rozmawia z botem. |
| **RAG** | Retrieval Augmented Generation - wzorzec: znajdź relevantne fragmenty dokumentów → wstaw do promptu → wywołaj LLM. |

---

## 2. Kontekst (C4 - level 1)

Co jest w systemie Multibot, co jest poza nim.

**Diagram:** [`../diagrams/c4-context.puml`](../diagrams/c4-context.puml) *(otwórz w IntelliJ z pluginem PlantUML)*

**Aktorzy zewnętrzni:**
- **Admin** - korzysta z panelu webowego (`panel.multibot.app`)
- **End user** - korzysta z widgetu osadzonego na stronie Tenanta (np. `acme.com`)

**Systemy zewnętrzne:**
- **OpenAI** - generowanie embeddingów (`text-embedding-3-small`)
- **Anthropic** - LLM do generowania odpowiedzi (Claude Haiku / Sonnet)
- **File storage** - przechowywanie wgranych plików (w MVP: dysk lokalny, później: S3)

---

## 3. Komponenty (C4 - level 2)

Co jest wewnątrz "Multibot Platform" - kontenery i ich połączenia.

**Diagram:** [`../diagrams/c4-container.puml`](../diagrams/c4-container.puml)

> *Auth w MVP odpuszczamy - tenant=1 hardcoded. Szczegóły w [multi-tenancy.md](multi-tenancy.md).*

---

## 4. Odpowiedzialności komponentów

### Frontend Panel (React)
- UI dla Admina: zarządzanie dokumentami, podgląd historii rozmów, statystyki, chat testowy.
- **Nie zawiera logiki biznesowej** - tylko renderuje stan z backendu.

### Widget JS (Preact, osobny bundle)
- Lekki (~50KB) chat embeddowany na stronie Tenanta.
- Komunikuje się tylko z publicznymi endpointami `/api/widget/*`.
- Identyfikuje się tenantowym widget keyem, używa Shadow DOM dla izolacji CSS.
- **W MVP:** nie ma. Zamiast tego hosted page (`/chat/{tenant-slug}`).

### Backend API (Spring Boot)
- Cały REST: auth, dokumenty, chat, statystyki.
- Trzyma `TenantContext` per request (źródło tenant_id dla wszystkich query).
- **Nie robi** ciężkiej roboty CPU/IO - to przerzuca na Indexer i RAG service.

### Document Indexer (async)
- Worker przetwarzający wgrane pliki: Tika → chunking → embeddingi → pgvector.
- W MVP: `@Async` w tym samym procesie. Architektura przygotowana na wyciągnięcie do osobnego procesu / kolejki.

### RAG Service
- Obsługuje zapytania chatu: embedding pytania → search w pgvector → budowa promptu → call do LLM → streaming odpowiedzi.
- **Zawsze** filtruje po `tenant_id` w vector store.

### PostgreSQL + pgvector
- Jedna baza na wszystko: dane relacyjne (tenants, users, documents, sessions, messages) + wektory (chunks z embeddingami).
- Multi-tenancy: kolumna `tenant_id` w każdej tabeli, filtrowanie na poziomie aplikacji.

### File Storage
- Surowe pliki wgrane przez Adminów (PDF, DOCX itp.).
- W MVP: katalog na dysku (`./storage/{tenant_id}/{document_id}`).
- Za interfejsem `FileStorage` - łatwa wymiana na S3 / Hetzner Storage Box w produkcji.

---

## 5. Główne przepływy (jednolinijkowo)

Pełne diagramy sekwencji w `flows/`.

1. **Upload dokumentu** - Admin uploaduje plik → API zapisuje + tworzy rekord `Document(status=pending)` → Indexer w tle robi extract+chunk+embed → status `ready`. *(szczegóły: [flows/document-upload.md](../flows/document-upload.md))*

2. **Zadanie pytania (RAG)** - End user wysyła wiadomość → API tworzy embedding pytania → wyszukuje top-K chunków z pgvector (filtr tenant_id) → buduje prompt (system + kontekst + historia) → call do LLM ze streamingiem → SSE do klienta → zapis Message w bazie. *(szczegóły: [flows/chat-rag.md](../flows/chat-rag.md))*

3. **Usunięcie dokumentu** - Admin usuwa dokument → kaskadowe usunięcie chunków z pgvector → usunięcie pliku ze storage.

4. **Onboarding Tenanta** *(post-MVP)* - rejestracja przez panel lub manualnie przez super-admina → utworzenie Tenanta + pierwszego Admina → wygenerowanie widget keya.

---

## 6. Granice MVP

Co budujemy teraz, co świadomie odkładamy.

**Diagram:** [`../diagrams/mvp-scope.puml`](../diagrams/mvp-scope.puml)

**MVP w jednym zdaniu:** działająca platforma lokalnie, gdzie uploaduję PDF-y, dostaję sensowne odpowiedzi oparte o te dokumenty z cytowaniem źródeł, jako jeden hardcodowany tenant.

---

## 7. Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Jak konkretnie izolujemy tenantów w kodzie | [multi-tenancy.md](multi-tenancy.md) |
| Jak wyglądają pakiety i warstwy w Spring Boot | [backend.md](backend.md) |
| Jakie są tabele i relacje w bazie | [../database/schema.md](../database/schema.md) |
| Jak działa pgvector i jak filtrujemy wektory | [../database/pgvector.md](../database/pgvector.md) |
| Jakie endpointy wystawia API | [../api/endpoints.md](../api/endpoints.md) |
| Krok po kroku jak działa upload | [../flows/document-upload.md](../flows/document-upload.md) |
| Krok po kroku jak działa chat | [../flows/chat-rag.md](../flows/chat-rag.md) |
