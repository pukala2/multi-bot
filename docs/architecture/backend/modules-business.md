# Moduły biznesowe i orkiestratory

Moduły **mające domenę biznesową**. Każdy zgodny z wzorcem hexagonalnym z [`hexagonal-pattern.md`](hexagonal-pattern.md).

Dwie podgrupy:

| Podgrupa | Charakterystyka | Lista modułów |
|----------|-----------------|---------------|
| **Business** | Mają domenę + wystawiają REST API | `tenant`, `user`, `document`, `chat` |
| **Orchestrator** | Mają domenę, ale brak REST API - wołane przez inne moduły lub eventy | `file`, `vector`, `indexing`, `rag` |

Spis modułów:
- [`tenant/`](#tenant) - zarządzanie tenantami
- [`user/`](#user) - userzy panelu (post-MVP)
- [`file/`](#file) - storage plików
- [`vector/`](#vector) - pgvector + tenant filter
- [`document/`](#document) - dokumenty
- [`indexing/`](#indexing) - async pipeline indeksacji
- [`chat/`](#chat) - sesje i wiadomości
- [`rag/`](#rag) - RAG orchestrator

---

## `tenant/`

**Cel:** zarządzanie tenantami (firmami-klientami). W MVP: jeden rekord w bazie, ale moduł istnieje by przygotować pełen multi-tenant.

**Status:** ✅ MVP (minimalnie)

### Domain model

| Klasa | Opis |
|-------|------|
| `Tenant` | record z `TenantId, name, slug, plan, status, createdAt`. Implementuje `TenantOwned` (zwraca własne ID jako tenantId). |
| `TenantSlug` | value object - URL-friendly identifier (`acme-corp`). Walidacja w konstruktorze. |
| `TenantStatus` | enum: `ACTIVE`, `SUSPENDED` |
| `TenantPlan` | enum: `FREE`, `BASIC`, `PRO` (po-MVP - na razie tylko `FREE`) |

### Use cases (port IN)

| Use case | Opis |
|----------|------|
| `CreateTenantUseCase` | Tworzenie nowego tenanta (post-MVP - super-admin / signup flow) |
| `GetTenantUseCase` | Pobranie tenanta po ID lub slug |
| `ListTenantsUseCase` | Lista tenantów (post-MVP - super-admin) |
| `UpdateTenantUseCase` | Update planu/statusu (post-MVP) |

### Porty OUT

| Port | Opis |
|------|------|
| `TenantRepository` | persistence tenanta |

### Eventy

| Typ | Event |
|-----|-------|
| Publikuje | `TenantCreatedEvent`, `TenantSuspendedEvent` (post-MVP) |
| Słucha | brak |

### REST API

| Endpoint | Use case | Status |
|----------|----------|--------|
| `GET /api/admin/tenants/{id}` | `GetTenantUseCase` | post-MVP (super-admin) |
| `GET /api/admin/tenants` | `ListTenantsUseCase` | post-MVP |
| `POST /api/admin/tenants` | `CreateTenantUseCase` | post-MVP |
| `PATCH /api/admin/tenants/{id}` | `UpdateTenantUseCase` | post-MVP |

### Stan w MVP

- Tabela `tenants` z jednym rekordem (id=1, name="Default Tenant", slug="default") seeded przez Flyway w `V1__seed_default_tenant.sql`
- Brak endpointów REST (tylko encja + repo)
- `TenantContext` zawsze zwraca `TenantId(1L)` (przez `MockTenantResolver` z `tenancy/`)

---

## `user/`

**Cel:** userzy panelu admina (pracownicy Tenanta). Każdy User należy do dokładnie jednego Tenanta.

**Status:** ❌ post-MVP (cały moduł)

### Domain model (planowany)

| Klasa | Opis |
|-------|------|
| `User` | record z `UserId, TenantId, email, passwordHash, role, createdAt`. Implementuje `TenantOwned`. |
| `UserEmail` | value object - walidacja formatu email |
| `UserRole` | enum: `ADMIN`, `MEMBER` |
| `PasswordHash` | value object - BCrypt |

### Use cases (planowane)

| Use case | Opis |
|----------|------|
| `RegisterUserUseCase` | rejestracja (na zaproszenie) |
| `AuthenticateUserUseCase` | login (email + password) - zwraca JWT |
| `ListUsersUseCase` | lista userów w tenant (dla admina) |
| `InviteUserUseCase` | wysłanie zaproszenia (email z linkiem) |
| `RemoveUserUseCase` | dezaktywacja usera |

### Stan w MVP

**Moduł nie istnieje.** Brak `/auth/login`, `/auth/register`, brak Spring Security. Cała aplikacja działa "anonymously" jako `tenant_id=1`.

Dorobimy razem z włączeniem multi-tenant (patrz tabela w [`../multi-tenancy.md`](../multi-tenancy.md)).

---

## `file/`

**Cel:** abstrakcja storage'u plików. Inne moduły (głównie `document/`) nie wiedzą czy plik leci na dysk lokalny, S3, czy do innego systemu.

**Status:** ✅ MVP (LocalFileStorageAdapter), post-MVP (S3FileStorageAdapter)

### Domain model

| Klasa | Opis |
|-------|------|
| `StoredFile` | record z `StoredFileId, TenantId, originalFilename, mimeType, size, storageKey, uploadedAt`. `storageKey` to wewnętrzny identyfikator (np. ścieżka relatywna) - inni nie powinni go interpretować. |
| `StoredFileId` | value object opakowujący `UUID` |
| `FileContent` | record z `byte[] bytes, String mimeType` - dane pliku + metadata |
| `StoreFileCommand` | record z `originalFilename, mimeType, byte[] bytes` |

### Use cases (port IN)

| Use case | Opis |
|----------|------|
| `StoreFileUseCase` | `StoredFileId store(StoreFileCommand)` - zapis pliku, zwraca ID |
| `ReadFileUseCase` | `FileContent read(StoredFileId)` - odczyt zawartości |
| `DeleteFileUseCase` | `void delete(StoredFileId)` - kaskadowo usuwa zapis + metadane |
| `GetFileMetadataUseCase` | `StoredFile getMetadata(StoredFileId)` - tylko metadane (bez bajtów) |

### Porty OUT

| Port | Opis |
|------|------|
| `FileStoragePort` | abstrakcja fizycznego storage'u - `write(key, bytes)`, `read(key)`, `delete(key)` |
| `StoredFileRepository` | persistence metadanych pliku |

### Infrastructure - adaptery

| Adapter | Implementuje | Aktywny w |
|---------|--------------|-----------|
| `LocalFileStorageAdapter` | `FileStoragePort` | MVP (`multibot.file.storage=local`) |
| `S3FileStorageAdapter` | `FileStoragePort` | post-MVP (`multibot.file.storage=s3`) |

### Konwencja ścieżek (LocalFileStorageAdapter)

```
./storage/
└── tenants/
    └── {tenantId}/
        └── files/
            └── {storedFileId}.{ext}
```

Tenant ID w ścieżce - dodatkowa warstwa izolacji. Mimo że metadane w DB filtrują, fizyczna struktura też.

### Eventy

| Typ | Event |
|-----|-------|
| Publikuje | brak (operacje synchroniczne) |
| Słucha | brak |

### REST API

Brak. Moduł wewnętrzny, wołany przez inne moduły.

### Notatki

- `StoredFile.storageKey` to **nieprzezroczysty** identyfikator - tylko adapter wie jak go zinterpretować. To pozwala swapować local→S3 bez zmiany API.
- Wszystkie operacje są tenant-aware przez `TenantContext` - adapter konstruuje ścieżkę z `TenantContext.get()`.
- Plik jest fizycznie usuwany od razu (nie soft-delete). Po-MVP można rozważyć soft-delete + GC job.

---

## `vector/`

**Cel:** wrapper na pgvector wymuszający filtr po `tenant_id`. Logicznie - "tenant-aware vector store".

**Status:** ✅ MVP

### Domain model

| Klasa | Opis |
|-------|------|
| `Chunk` | record z `ChunkId, TenantId, DocumentId, content (text), Embedding embedding, chunkIndex, ChunkMetadata`. Implementuje `TenantOwned`. |
| `Embedding` | record opakowujący `float[]` - typowo 1536 wymiarów |
| `ChunkMetadata` | record z dodatkowymi metadanymi: `pageNumber, sectionTitle, sourceFilename` |
| `VectorSearchQuery` | record z `Embedding queryEmbedding, int topK, double minScore` |
| `VectorSearchResult` | record z `Chunk chunk, double similarityScore` |

### Use cases (port IN)

| Use case | Opis |
|----------|------|
| `StoreChunksUseCase` | `void storeAll(List<Chunk>)` - bulk write, zawsze dodaje filtr tenant_id z TenantContext |
| `SearchChunksUseCase` | `List<VectorSearchResult> search(VectorSearchQuery)` - filtr tenant_id WYMUSZONY |
| `DeleteChunksByDocumentUseCase` | `void deleteByDocument(DocumentId)` - kaskadowe czyszczenie przy usunięciu dokumentu |
| `CountChunksByDocumentUseCase` | `long count(DocumentId)` - do statystyk |

### Porty OUT

| Port | Opis |
|------|------|
| `ChunkStore` | abstrakcja vector store - operacje na chunkach |

### Infrastructure

- `PgVectorChunkStoreAdapter` - implementuje `ChunkStore` używając Spring AI `VectorStore` (pgvector implementation)
- `SpringAiVectorStoreConfig` - `@Bean VectorStore` (pgvector, wymiar 1536, similarity = cosine)
- Tabela `chunks` z kolumną `embedding vector(1536)` + indeks HNSW lub IVFFlat (decyzja → [`../database/pgvector.md`](../../database/pgvector.md))

### Tenant filter - jak działa

`PgVectorChunkStoreAdapter` przy każdym `search()`:
1. Czyta `TenantContext.get()`
2. Buduje `SearchRequest.builder().query(...).filterExpression("tenant_id == " + tenantId)`
3. Deleguje do Spring AI `VectorStore.similaritySearch(request)`
4. Mapuje wyniki na `VectorSearchResult` z domain model

**Defense in depth:** dodatkowo serwis może wywołać `TenantSecurityCheck.assertSameTenant(chunk)` po pobraniu - na wypadek gdyby filtr Spring AI nie zadziałał (np. bug w bibliotece).

### Eventy

| Typ | Event |
|-----|-------|
| Publikuje | brak |
| Słucha | brak |

### REST API

Brak.

---

## `document/`

**Cel:** zarządzanie dokumentami uploadowanymi przez Admina. CRUD + status indeksacji.

**Status:** ✅ MVP

### Domain model

| Klasa | Opis |
|-------|------|
| `Document` | record z `DocumentId, TenantId, filename, mimeType, sizeBytes, status, StoredFileId, uploadedBy, uploadedAt, indexedAt`. Implementuje `TenantOwned`. |
| `DocumentId` | value object opakowujący `Long` (lub UUID - do decyzji w schema) |
| `DocumentStatus` | enum: `PENDING`, `PROCESSING`, `READY`, `FAILED` |
| `DocumentSummary` | "lekka" wersja Document dla listy (bez storage key etc.) |
| `UploadDocumentCommand` | record z `filename, mimeType, byte[] content, uploadedByUserId (opcjonalne dla MVP)` |

### Use cases (port IN)

| Use case | Opis |
|----------|------|
| `UploadDocumentUseCase` | upload pliku - zapisuje metadane + bajty + publikuje event |
| `DeleteDocumentUseCase` | usunięcie - kaskadowo: chunki (vector/) + plik (file/) + rekord |
| `ListDocumentsUseCase` | lista dokumentów tenanta z paginacją |
| `GetDocumentStatusUseCase` | tylko status (lekki endpoint do pollowania z FE) |
| `GetDocumentUseCase` | pełne dane jednego dokumentu |

### Porty OUT

| Port | Opis |
|------|------|
| `DocumentRepository` | persistence dokumentu |

### Zależności od innych modułów

| Moduł | Co używa | Po co |
|-------|----------|-------|
| `file/` | `StoreFileUseCase`, `DeleteFileUseCase` | zapis i usunięcie bajtów pliku |
| `vector/` | `DeleteChunksByDocumentUseCase` | kaskadowe czyszczenie chunków przy delete |

### Eventy

| Typ | Event | Opis |
|-----|-------|------|
| Publikuje | `DocumentUploadedEvent` | po zapisaniu metadanych + bajtów - wyzwala async indeksację |
| Publikuje | `DocumentDeletedEvent` | po usunięciu - dla analytics (post-MVP) |
| Słucha | `IndexingCompletedEvent` | update `status` na `READY` + ustawia `indexedAt` |
| Słucha | `IndexingFailedEvent` | update `status` na `FAILED` + zapisuje błąd |

### REST API

| Endpoint | Use case | Opis |
|----------|----------|------|
| `POST /api/documents` | `UploadDocumentUseCase` | multipart upload |
| `GET /api/documents` | `ListDocumentsUseCase` | paginacja, sortowanie |
| `GET /api/documents/{id}` | `GetDocumentUseCase` | szczegóły |
| `GET /api/documents/{id}/status` | `GetDocumentStatusUseCase` | lekki polling |
| `DELETE /api/documents/{id}` | `DeleteDocumentUseCase` | usunięcie |

### Flow uploadu (szkic - szczegóły w [`flows/document-upload.md`](../../flows/document-upload.md))

1. Controller dostaje multipart - waliduje rozmiar, mime type
2. Service: woła `file.StoreFileUseCase` → dostaje `StoredFileId`
3. Service: zapisuje `Document(status=PENDING, storedFileId=...)` przez repo
4. Service: publikuje `DocumentUploadedEvent`
5. Response: 201 z `DocumentResponse(status=PENDING)`
6. Asynchronicznie: `indexing/` łapie event, robi swoje, publikuje `IndexingCompletedEvent` lub `IndexingFailedEvent`
7. `document/` łapie ten event, update status

### Notatki

- Limit rozmiaru pliku (np. 50MB) - na poziomie Spring (`spring.servlet.multipart.max-file-size`)
- Limit liczby plików per tenant (po-MVP) - polityka per plan
- W MVP wszystkie statusy aktualizowane synchronicznie w bazie. Po-MVP można rozważyć WebSocket / SSE notifikacje do FE

---

## `indexing/`

**Cel:** async pipeline: czyta plik → ekstraktuje tekst (Tika) → tnie na chunki → generuje embeddingi → zapisuje do vector store.

**Status:** ✅ MVP

### Domain model

| Klasa | Opis |
|-------|------|
| `IndexingJob` | record z `JobId, DocumentId, TenantId, status, errorMessage, startedAt, completedAt, chunksCreated`. Implementuje `TenantOwned`. |
| `JobStatus` | enum: `RUNNING`, `COMPLETED`, `FAILED` |
| `ExtractedText` | record z `String content, ExtractedMetadata metadata` - wynik Tiki |
| `ExtractedMetadata` | record z `pageCount, language (jeśli wykryty)` |
| `ChunkingPolicy` | klasa konfiguracyjna: rozmiar chunka, overlap, strategia |

### Use cases (port IN)

| Use case | Opis |
|----------|------|
| `IndexDocumentUseCase` | wewnętrzny - wołany przez listener eventu `DocumentUploaded` |
| `RetryIndexingUseCase` | post-MVP - manualny retry dla failed dokumentów |

### Porty OUT

| Port | Opis |
|------|------|
| `FileReader` | abstrakcja na czytanie pliku + ekstrakcję tekstu |
| `IndexingJobRepository` | persistence stanu jobów |

### Infrastructure

- `TikaFileReaderAdapter` - implementuje `FileReader`, używa Apache Tika do ekstrakcji tekstu z PDF/DOCX/TXT/MD
- Wstrzykuje `file.ReadFileUseCase` (z modułu `file/`) żeby dostać bajty

### Zależności od innych modułów

| Moduł | Co używa | Po co |
|-------|----------|-------|
| `file/` | `ReadFileUseCase` | pobranie bajtów pliku |
| `ai/` | `EmbeddingModel` (bezpośrednio przez Spring AI bean), `TokenTextSplitter` | generowanie embeddingów + chunking |
| `vector/` | `StoreChunksUseCase` | zapis chunków z embeddingami |

### Eventy

| Typ | Event | Opis |
|-----|-------|------|
| Publikuje | `IndexingCompletedEvent` | po sukcesie - zawiera `DocumentId, TenantId, chunksCreated, durationMs` |
| Publikuje | `IndexingFailedEvent` | po błędzie - zawiera `DocumentId, TenantId, errorMessage` |
| Słucha | `DocumentUploadedEvent` | wyzwala `IndexDocumentUseCase` |

### REST API

Brak - moduł wewnętrzny.

### Flow indeksacji (szkic)

1. Listener łapie `DocumentUploadedEvent`
2. Tworzy `IndexingJob(status=RUNNING, startedAt=now)`
3. Woła `file.ReadFileUseCase` → bajty
4. Woła `tika.extract(bytes, mimeType)` → tekst + metadata
5. Woła `tokenTextSplitter.split(text)` → lista stringów (chunków)
6. Dla każdego chunka: woła `embeddingModel.embed(text)` → `float[]`
7. Buduje listę `Chunk` (z `TenantContext.get()`)
8. Woła `vector.StoreChunksUseCase.storeAll(chunks)`
9. Update job: status=COMPLETED
10. Publikuje `IndexingCompletedEvent`

Pełny diagram → [`../../flows/document-upload.md`](../../flows/document-upload.md).

### Notatki

- **Tenant propagation w async**: `@ApplicationModuleListener` z Spring Modulith Events automatycznie propaguje TenantContext - patrz [`events.md`](events.md)
- **Embedding model batch API**: post-MVP - na razie jeden chunk = jedno wywołanie. Optimization: batch np. 10 chunków na request → mniej latency i kosztów.
- **Retry logic**: jeśli OpenAI API zawiedzie (rate limit, timeout) - exponential backoff, max 3 próby. Po 3 niepowodzeniach: status FAILED.

---

## `chat/`

**Cel:** zarządzanie sesjami chatu (End user ↔ bot) + storage wiadomości + streaming odpowiedzi.

**Status:** ✅ MVP

### Domain model

| Klasa | Opis |
|-------|------|
| `ChatSession` | record z `SessionId, TenantId, endUserId (Optional dla anonimowych), startedAt, lastActivityAt, metadata`. Implementuje `TenantOwned`. |
| `Message` | record z `MessageId, SessionId, TenantId, role, content, sources (List<Citation>), tokensUsed, createdAt`. Implementuje `TenantOwned`. |
| `MessageRole` | enum: `USER`, `ASSISTANT`, `SYSTEM` |
| `EndUserIdentity` | record - `Optional<String> externalId, String anonymousId` - identyfikacja end usera |
| `MessageStream` | reactive stream (`Flux<MessageChunk>`) - dla SSE |

### Use cases (port IN)

| Use case | Opis |
|----------|------|
| `StartSessionUseCase` | utworzenie nowej sesji (anonimowo lub z external_id) |
| `SendMessageUseCase` | wysłanie wiadomości - zwraca `Flux<MessageChunk>` (SSE stream) |
| `GetSessionHistoryUseCase` | historia wiadomości w sesji |
| `ListSessionsUseCase` | lista sesji tenanta (dla admina - przegląd) |

### Porty OUT

| Port | Opis |
|------|------|
| `ChatRepository` | persistence sesji i wiadomości |

### Zależności od innych modułów

| Moduł | Co używa | Po co |
|-------|----------|-------|
| `rag/` | `AnswerQuestionUseCase` | główna logika RAG (search + LLM call) |

### Eventy

| Typ | Event | Opis |
|-----|-------|------|
| Publikuje | `MessageSentEvent` | post-MVP, do analytics (najczęstsze pytania, koszty) |
| Słucha | brak |

### REST API

| Endpoint | Use case | Opis |
|----------|----------|------|
| `POST /api/chat/sessions` | `StartSessionUseCase` | utworzenie sesji |
| `GET /api/chat/sessions` | `ListSessionsUseCase` | lista (dla admina) |
| `GET /api/chat/sessions/{id}/messages` | `GetSessionHistoryUseCase` | historia |
| `POST /api/chat/sessions/{id}/messages` | `SendMessageUseCase` | wysłanie + SSE response |

**SSE response format** (dla `SendMessageUseCase`):
```
event: chunk
data: { "text": "część odpowiedzi..." }

event: chunk
data: { "text": "kolejna część..." }

event: sources
data: { "sources": [{ "documentId": ..., "chunkId": ..., "snippet": "..." }] }

event: done
data: { "messageId": ..., "tokensUsed": 1234 }
```

### Flow wysłania wiadomości (szkic)

1. Controller dostaje request, waliduje
2. Service: zapisuje `Message(role=USER, content=...)` przez repo
3. Service: pobiera historię ostatnich N wiadomości (memory window)
4. Service: woła `rag.AnswerQuestionUseCase(query, history)` → `Flux<RagAnswerChunk>`
5. Service: mapuje stream na SSE eventy → leci do klienta
6. Po zakończeniu streamu: zapisuje `Message(role=ASSISTANT, content=zebrana_odpowiedź, sources=...)` przez repo
7. Publikuje `MessageSentEvent` (post-MVP)

Pełny diagram → [`../../flows/chat-rag.md`](../../flows/chat-rag.md).

### Notatki

- **Pamięć kontekstu** - last N (np. 10) wiadomości doklejane do promptu. Konfigurowalna przez property `multibot.chat.memory.windowSize`.
- **End user identification** - w MVP zawsze anonymous (`anonymousId` z cookie / sessionStorage). Post-MVP: opcjonalny `external_id` od klienta (`?user_id=acme-123`).
- **Streaming**: używamy Spring `SseEmitter` lub WebFlux `Flux<ServerSentEvent>`. Decyzja zależy od tego czy aplikacja ma być Servlet czy WebFlux - rekomenduję Servlet + `SseEmitter` dla MVP (mniej zmiana stacku).
- **Rate limiting**: post-MVP - per session / per tenant. W MVP jedna polityka aplikacyjna.

---

## `rag/`

**Cel:** orchestrator RAG. Łączy search w vector store + budowę promptu + call do LLM ze streamingiem.

**Status:** ✅ MVP

### Domain model

| Klasa | Opis |
|-------|------|
| `RagQuery` | record z `TenantId, question, List<Message> history, RagOptions options` |
| `RagOptions` | record z `topK (np. 5), minScore (np. 0.7), maxTokens, temperature` |
| `RagAnswer` | record z `String text, List<Citation> citations, int tokensUsed` |
| `RagAnswerChunk` | record dla streamu - `String textDelta` (delta tokenów) lub `List<Citation>` (finalnie) |
| `Citation` | record z `ChunkId, DocumentId, String snippet (krótki fragment), double score` |
| `PromptContext` | record z `String systemPrompt, List<Chunk> retrievedChunks, List<Message> history, String userQuestion` |

### Use cases (port IN)

| Use case | Opis |
|----------|------|
| `AnswerQuestionUseCase` | `Flux<RagAnswerChunk> answer(RagQuery)` - główna metoda |

### Porty OUT

Brak własnych - używa innych modułów przez ich port IN.

### Zależności od innych modułów

| Moduł | Co używa | Po co |
|-------|----------|-------|
| `vector/` | `SearchChunksUseCase` | retrieval relevantnych chunków |
| `ai/` | `ChatClient` (bezpośrednio przez Spring AI bean), `EmbeddingModel` | embedding pytania + call do LLM |

### Application classes

- `RagService` - implementuje `AnswerQuestionUseCase`, orchestracja całego flow
- `PromptBuilder` - składa prompt: system + kontekst + historia + pytanie. Polskie prompty (kopiujemy z `insurance-assistant`)
- `CitationExtractor` - po otrzymaniu odpowiedzi z LLM mapuje `retrievedChunks` na `Citation` (na podstawie ID użytych w odpowiedzi lub po prostu top-K wszystkich pobranych)

### Eventy

| Typ | Event |
|-----|-------|
| Publikuje | brak (synchronicznie odpowiada na `chat/`) |
| Słucha | brak |

### REST API

Brak - moduł wewnętrzny.

### Flow RAG (szkic)

1. `chat.SendMessageUseCase` woła `rag.AnswerQuestionUseCase(query)`
2. RagService: woła `embeddingModel.embed(query.question)` → embedding pytania
3. RagService: woła `vector.SearchChunksUseCase(queryEmbedding, topK=5)` → `List<VectorSearchResult>`
4. RagService: filter `minScore` - odrzuca słabe matche
5. RagService: `PromptBuilder.build(systemPrompt, chunks, history, question)` → final prompt
6. RagService: `chatClient.prompt(prompt).stream()` → `Flux<String>` (tokeny)
7. RagService: mapuje `Flux<String>` na `Flux<RagAnswerChunk>` z delta tokenami
8. Na końcu strumienia: emituje `RagAnswerChunk` z `citations` (Lista z kroku 3)

Pełny diagram → [`../../flows/chat-rag.md`](../../flows/chat-rag.md).

### Notatki

- **System prompt** - krytyczny dla jakości. Musi mówić: "odpowiadaj TYLKO na podstawie kontekstu, jeśli nie wiesz - powiedz że nie wiesz". Inaczej Claude halucynuje.
- **Reranker** - w MVP brak. Top-K wyników z vector search idzie bezpośrednio do promptu. Post-MVP można dorzucić reranker (np. Cohere Rerank) który poprawia jakość.
- **Hybrid search** (vector + BM25) - post-MVP. Spring AI tego nie ma out-of-the-box, trzeba samemu.
- **Citation matching** - prosta strategia w MVP: wszystkie pobrane chunki = citations. Bardziej precyzyjna strategia (LLM zwraca ID użytych źródeł) - post-MVP.

---

## Spring Modulith - relacje między modułami

W `package-info.java` każdego modułu deklarujemy `@ApplicationModule(allowedDependencies = {...})`. Pełna mapa:

| Moduł | Pozwoleni sąsiedzi |
|-------|-------------------|
| `tenant` | `shared`, `tenancy` |
| `user` | `shared`, `tenancy`, `tenant` |
| `file` | `shared`, `tenancy` |
| `vector` | `shared`, `tenancy`, `ai` |
| `document` | `shared`, `tenancy`, `file`, `vector` (cascade delete) |
| `indexing` | `shared`, `tenancy`, `ai`, `file`, `vector`, `document` (słucha eventu) |
| `chat` | `shared`, `tenancy`, `rag` |
| `rag` | `shared`, `tenancy`, `ai`, `vector` |
| `tenancy`, `config`, `ai`, `shared` | (open - infrastructure) |

**Brak cykli** - test Modulith failuje jeśli wprowadzony.

---

## Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Strategia ogólna backendu | [`overview.md`](overview.md) |
| Wzorzec hexagonalny per moduł | [`hexagonal-pattern.md`](hexagonal-pattern.md) |
| Moduły infrastrukturalne (shared, tenancy, config, ai) | [`modules-infrastructure.md`](modules-infrastructure.md) |
| Spring Modulith setup + jak działają eventy | [`events.md`](events.md) |
| Konfiguracja, błędy, testy | [`operations.md`](operations.md) |
| Strategia multi-tenancy | [`../multi-tenancy.md`](../multi-tenancy.md) |
| Pełny flow uploadu (sequence diagram) | [`../../flows/document-upload.md`](../../flows/document-upload.md) |
| Pełny flow chatu (sequence diagram) | [`../../flows/chat-rag.md`](../../flows/chat-rag.md) |
