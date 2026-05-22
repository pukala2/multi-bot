# Plan implementacji MVP - backend

Vertical slices, bottom-up po grafie zależności. Każda faza = działający increment, jeden commit (lub kilka jeśli duża).

**Workflow:** dla każdej fazy najpierw szczegółowy plan (co, jak, jakie klasy/testy) → akceptacja → implementacja → review → commit.

---

## Filozofia

- **Vertical slices, nie warstwy** - po każdej fazie coś realnie działa
- **Bottom-up po dependencies** - foundation → file/vector → document → indexing → chat/rag
- **Hexagonal od dnia 1** - bez "potem zrefactorujemy"
- **Każda faza ma jasne acceptance criteria** - wiadomo kiedy skończona
- **Test-driven** - integration test per moduł, jeden E2E na końcu

---

## Fazy

### Faza 0: Foundation (~1 dzień)

**Cel:** App bootuje się, infrastruktura modułów cross-cutting gotowa.

**Implementujemy:**
- `shared/` - `TenantId`, `TenantOwned`, hierarchia exceptions
- `tenancy/` - `TenantContext`, Filter, `MockTenantResolver`, `TenantIdResolver` (Hibernate)
- `config/` - Web, Async (z tenant propagation), OpenAPI, Jackson, GlobalExceptionHandler
- `ai/` - SpringAiConfig (ChatClient, EmbeddingModel beans)

**Acceptance:**
- `./mvnw spring-boot:run` startuje bez błędów
- `GET /actuator/health` → 200 `UP`
- `GET /swagger-ui.html` renderuje (puste, ale działa)
- `ApplicationModulesTest` passes

---

### Faza 1: Tenant + DB schema base (~0.5 dnia)

**Cel:** Default tenant w bazie, fundament tabel z `tenant_id`.

**Implementujemy:**
- Migracje Flyway: `tenants` table + seed default tenant (UUID `00000000-0000-7000-8000-000000000001`)
- `tenant/` moduł (czysty hexagonal - mały zakres)
- `MockTenantResolver` zwraca konkretny default UUID
- Bez REST API

**Acceptance:**
- Tabela `tenants` istnieje, jeden rekord po starcie
- `TenantContext.get()` zwraca default UUID
- Unit test: `GetTenantUseCase.getDefault()` zwraca Tenant

---

### Faza 2: File module (~0.5 dnia)

**Cel:** Storage plików (dysk + metadane).

**Implementujemy:**
- Migracja: `stored_files` table
- `file/` moduł: domain (`StoredFile`, `StoredFileId`), use cases, `LocalFileStorageAdapter`
- Bez REST (moduł wewnętrzny)

**Acceptance:**
- Integration test (Testcontainers): store bytes → read same bytes
- Plik fizycznie w `./storage/tenants/{tenantId}/{uuid}.ext`

---

### Faza 3: Vector module + pgvector (~1 dzień)

**Cel:** Zapis i wyszukiwanie wektorów z wymuszonym filtrem `tenant_id`.

**Implementujemy:**
- Migracja: `chunks` table + HNSW index
- `vector/` moduł: domain, use cases
- `PgVectorChunkStoreAdapter` (custom JdbcTemplate)
- `VectorService` wymusza filter `tenant_id = TenantContext.get()`

**Acceptance:**
- Integration test: store 10 chunków → search zwraca top-K
- Test izolacji: tenant A nie widzi chunków B (`@WithTenant` ustawia różne ID)

---

### Faza 4: Document module + upload endpoint (~1 dzień)

**Cel:** Mogę uploadować PDF, dostaję 202, widzę w liście.

**Implementujemy:**
- Migracja: `documents` table
- `document/` - **pierwszy moduł z REST API**
- `DocumentController` implementuje generated `DocumentsApi` (z OpenAPI)
- Publikuje `DocumentUploadedEvent`

**Acceptance:**
- `POST /api/documents` → 202 + Document(status=PENDING)
- `GET /api/documents` → lista
- `GET /api/documents/{id}/status` → status
- `DELETE /api/documents/{id}` → 204

---

### Faza 5: Indexing pipeline (~1.5 dnia)

**Cel:** Upload PDF → automatyczna indeksacja → status READY.

**Implementujemy:**
- Migracje: `indexing_jobs` + `event_publication` (Modulith)
- `indexing/`: domain, `TikaFileReaderAdapter`, `@ApplicationModuleListener`
- **Idempotency** (status check + cleanup + atomic transition)
- `DocumentStatusUpdater` w `document/` (listener IndexingCompletedEvent)

**Acceptance:**
- Upload PDF → po ~10s status `READY`
- `SELECT COUNT(*) FROM chunks` > 0
- Crash test: ubicie procesu w środku → restart → kontynuacja

---

### Faza 6: RAG + Chat (blocking, bez streamingu) (~1.5 dnia)

**Cel:** Zadaję pytanie, dostaję odpowiedź z cytowaniem.

**Implementujemy:**
- Migracje: `chat_sessions` + `messages`
- `chat/`: sessions + messages + ChatController (4 endpointy)
- `rag/`: PromptBuilder (polski prompt), RagService (embed → search → build → LLM)
- **Bez streamingu** - blocking JSON response

**Acceptance:**
- `POST /api/chat/sessions` → 201
- `POST /messages` → blocking JSON z `content` + `sources`
- Test: pytanie o PDF → cytaty z tego PDFa

---

### Faza 7: SSE streaming (~0.5 dnia)

**Cel:** Odpowiedź wpisuje się w czasie rzeczywistym.

**Implementujemy:**
- Refactor `SendMessageUseCase` → `Flux<RagAnswerChunk>`
- `ChatController` → `SseEmitter`
- `chatClient.stream()` zamiast `.call()`
- Format SSE: `chunk` / `sources` / `done`

**Acceptance:**
- `curl -N` widzi tokens w czasie rzeczywistym
- Latency to first token < 2s

---

### Faza 8: Polish + E2E test (~0.5-1 dzień)

**Cel:** MVP "production ready" lokalnie.

**Implementujemy:**
- MDC logging (tenantId, requestId)
- Walidacja edge cases
- `@WithTenant` testowa adnotacja
- **End-to-end test**: dwóch tenantów, upload, chat, izolacja zachowana

**Acceptance:**
- E2E test zielony
- Demo działa lokalnie

---

## Szacunek czasowy

| Faza | Dni roboczych |
|------|---------------|
| 0. Foundation | 1.0 |
| 1. Tenant | 0.5 |
| 2. File | 0.5 |
| 3. Vector + pgvector | 1.0 |
| 4. Document + upload | 1.0 |
| 5. Indexing pipeline | 1.5 |
| 6. RAG + Chat blocking | 1.5 |
| 7. SSE streaming | 0.5 |
| 8. Polish + E2E | 0.5-1.0 |
| **Razem** | **7.5-8.5** |

Dla nauki realnie ×2-3 = **~3-4 tygodnie kalendarzowe** przy pracy popołudniami.

---

## Co świadomie odpuszczamy w MVP

- Auth (login/register/JWT)
- `user/` moduł
- Widget JS embed
- Admin endpoints
- S3 storage
- Frontend (testujemy curl/Postman)
- Production deployment

---

## Workflow per faza

1. Plan szczegółowy (klasy, metody, testy) → akceptacja
2. Implementacja
3. Local test (manual + auto)
4. Review
5. Commit

---

## Linki

- Architektura: [`architecture/`](architecture/)
- Schemat bazy: [`database/schema.md`](database/schema.md)
- API spec: [`api/openapi.yaml`](api/openapi.yaml)
- Flows: [`flows/`](flows/)
