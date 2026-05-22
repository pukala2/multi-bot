# Flow: Upload + indeksacja dokumentu

Pełen flow od kliknięcia "Upload" przez Admina aż do chunków zaindeksowanych w pgvector i statusu `READY` w bazie.

**Diagram:** [`../diagrams/document-upload-flow.puml`](../diagrams/document-upload-flow.puml)

---

## Trzy fazy

| Faza | Czas | Charakter | Co się dzieje |
|------|------|-----------|---------------|
| **1. Upload** | ~100-500ms | Synchroniczny (HTTP request) | Plik zapisany, metadane w DB, event opublikowany |
| **2. Indeksacja** | ~5-60s zależnie od rozmiaru | Asynchroniczny (Spring Modulith Event) | Tika → chunking → embeddingi → pgvector |
| **3. Update statusu** | <100ms | Asynchroniczny (Spring Modulith Event) | `status=READY` na dokumencie |

Admin dostaje response **202 Accepted** już po fazie 1. Indeksacja leci w tle. Frontend pollluje `/status` żeby pokazać postęp.

---

## Faza 1: Upload (synchroniczny)

### Krok 1: HTTP request
```
POST /api/documents
Content-Type: multipart/form-data

file: <bajty PDF>
filename: "owu-pzu-2025.pdf"
```

### Krok 2: Controller - walidacja
- `@Valid` na `UploadDocumentRequest`
- Sprawdza: filename present, mime type whitelist (PDF/DOCX/TXT/MD), rozmiar w limicie (50MB - przez Spring `spring.servlet.multipart.max-file-size`)
- Niepowodzenie → 400 (ValidationProblemDetail)

### Krok 3: Service - transakcja
`DocumentService.uploadDocument(command)` opakowane w `@Transactional`. Wszystko poniżej w jednej transakcji:

1. **`FileService.storeFile(bytes, mimeType, filename)`** - moduł `file/`:
   - Zapis bajtów na dysk: `./storage/tenants/{tenantId}/{newUuid}.pdf`
   - INSERT do `stored_files`
   - Zwrot `StoredFileId`

2. **`DocumentRepository.save(Document)`**:
   - Nowy `Document` z `status=PENDING`, `storedFileId`, `uploadedAt=now`
   - INSERT do `documents`

3. **`events.publishEvent(DocumentUploadedEvent)`**:
   - Spring Modulith przechwytuje
   - INSERT do `event_publication` (outbox)
   - Wszystko w **tej samej transakcji** - jeśli rollback, event NIE idzie

### Krok 4: COMMIT
Po pomyślnym commicie transakcji:
- Spring Modulith oznacza event jako "do wysłania"
- Worker thread (async) zacznie obsługę

### Krok 5: Response
- HTTP **202 Accepted**
- Body: `DocumentResponse(status=PENDING)`
- Header: `Location: /api/documents/{id}`

---

## Faza 2: Indeksacja (async)

`IndexingEventListener.onDocumentUploaded(DocumentUploadedEvent)` wywołany przez Spring Modulith po commicie fazy 1. TenantContext ustawiony automatycznie.

### Krok 1: Idempotency check

**Krytyczne dla at-least-once delivery** (patrz [`../architecture/backend/events.md`](../architecture/backend/events.md) sekcja 9).

```
1. Pobierz Document.
2. Jeśli status == READY → SKIP (event już obsłużony wcześniej)
3. Cleanup poprzedniej (niepełnej) próby:
   - DELETE FROM chunks WHERE document_id = ?
   - DELETE FROM indexing_jobs WHERE document_id = ?
```

### Krok 2: Tranzycja statusu
```sql
UPDATE documents SET status='PROCESSING' 
WHERE id=? AND status='PENDING'
```

**Atomowy update z warunkiem** - jeśli ktoś inny równocześnie zmienił status (np. concurrent retry), my nie nadpiszemy.

### Krok 3: Job tracking
```sql
INSERT INTO indexing_jobs (status='RUNNING', started_at=now, ...)
```

### Krok 4: File reading
`file.ReadFileUseCase.readFile(storedFileId)` → bajty z dysku.

### Krok 5: Text extraction - Apache Tika
- `Tika.parseToString(bytes, mimeType)` → string z tekstem
- Plus metadata: liczba stron, język (jeśli wykryty)
- **Edge cases:**
  - PDF z obrazami (skanowane) - Tika zwraca pusty tekst → status FAILED ("dokument nie zawiera tekstu")
  - Zaszyfrowane PDF - exception → FAILED
  - Bardzo długi tekst - na razie obrabiamy w całości, post-MVP rozważyć streaming

### Krok 6: Chunking
- `TokenTextSplitter(chunkSize=500, overlap=50)`
- Zwraca listę chunków (każdy ~500 tokenów, z 50-tokenowym overlap dla kontekstu)
- Liczba chunków zależy od dokumentu: PDF 10 stron ~ 30-60 chunków

### Krok 7: Embeddingi (per chunk)
- Dla każdego chunka: `EmbeddingModel.embed(text)` → `float[1536]`
- Wewnętrznie call do OpenAI: `POST /v1/embeddings`
- **Koszt:** ~$0.00002 za 1000 tokenów. PDF 10 stron ~ $0.001
- **Czas:** ~100-300ms per chunk = 5-20s dla średniego dokumentu
- **Retry:** exponential backoff dla 429 (rate limit) i 5xx
- **Post-MVP optymalizacja:** batch API (10 chunków/request) → 10x szybsze, mniej kosztu

### Krok 8: Zapis do pgvector
```
vector.StoreChunksUseCase.storeAll(List<Chunk>)
```
- Każdy `Chunk` ma `tenant_id` z `TenantContext.get()` (przez `VectorService`)
- INSERT batch do `chunks` tabeli
- Embedding zapisany jako `VECTOR(1536)`
- HNSW index automatycznie aktualizowany

### Krok 9: Job completion
```sql
UPDATE indexing_jobs 
SET status='COMPLETED', completed_at=now, chunks_created=N
```

### Krok 10: Event publikacja
```
events.publishEvent(IndexingCompletedEvent(documentId, tenantId, chunksCreated))
```

Spring Modulith oznacza completion_date dla `DocumentUploadedEvent` (faza 2 zakończona) i tworzy nowy wpis w outbox dla `IndexingCompletedEvent`.

---

## Faza 3: Update statusu dokumentu (async)

`DocumentStatusUpdater.onIndexingCompleted(IndexingCompletedEvent)` w module `document/`.

```sql
UPDATE documents 
SET status='READY', indexed_at=now 
WHERE id=? AND status='PROCESSING'
```

Frontend pollujący `GET /api/documents/{id}/status` widzi `status=READY` przy następnym wywołaniu.

---

## Obsługa błędów

### Faza 1 (synchroniczna)

| Błąd | HTTP | Co się dzieje |
|------|------|---------------|
| Walidacja (filename empty, zły mime) | 400 | Brak side effects |
| Plik >50MB | 413 | Brak side effects |
| Crash przy `storeFile()` (dysk pełny) | 500 | Rollback transakcji - nic nie zapisane |
| Crash przy `repo.save()` | 500 | Rollback - plik usunięty (NIE - plik został! file/ jest poza transakcją bazy. **Problem do rozwiązania w impl** - patrz uwaga niżej) |
| Crash przy `publishEvent()` (rzadkie) | 500 | Rollback - dokument nie zapisany, plik został na dysku (orphan) |

**Uwaga - orphan files:** `file/` zapisuje na dysk **przed** commitem transakcji bazy. Jeśli transakcja rollback po `storeFile()` - plik został na dysku ale w `stored_files` jest rollbacked → orphan.

Strategie:
- **Opcja A (MVP):** ignorujemy. Garbage collection job czyści orphan files (np. raz dziennie - "stored_files records bez referencji w documents/od >1h").
- **Opcja B:** odłożyć zapis na dysk PO commicie - ale wtedy event "DocumentUploaded" leci zanim plik fizycznie istnieje, indexing pada.
- **Opcja C:** transactional outbox dla file operations - wymaga dodatkowej infrastruktury, overkill dla MVP.

Wybieramy **A** - GC cleanup orphans, zaakceptowane ryzyko marginalne dla MVP.

### Faza 2 (async indeksacja)

| Błąd | Status dokumentu | Co dalej |
|------|------------------|----------|
| Tika nie potrafi przetworzyć | `FAILED` + errorMessage | Admin widzi w UI, może usunąć i wgrać ponownie |
| OpenAI rate limit (429) | `RUNNING` z retry | 3 próby z exponential backoff, potem `FAILED` |
| OpenAI timeout / 5xx | jw. | jw. |
| Crash app w środku indeksacji | `PROCESSING` w bazie, event pending | Restart app → Modulith republikuje event → idempotency check cleanup + nowy start |
| pgvector failure | `FAILED` | Manual retry przez admin (post-MVP - dla MVP - usunięcie + wgranie ponownie) |

Po `IndexingFailedEvent`:
```
DocumentStatusUpdater.onIndexingFailed(event):
  UPDATE documents 
  SET status='FAILED', error_message=event.errorMessage 
  WHERE id=?
```

### Faza 3

Mało może pójść nie tak - prosty UPDATE. Jeśli pada → event zostaje pending → republikacja przy restarcie.

---

## Decyzje architektoniczne udokumentowane

### Dlaczego event zamiast bezpośredniego wywołania `IndexingService` z `DocumentService`?

**Sync (zamiast eventu):**
```
documentService.upload() {
    fileService.store(...)
    documentRepo.save(...)
    indexingService.index(...)  // ← sync call
}
```

Plusy: prosto, jedna transakcja, deterministyczne.
Minusy:
- HTTP request blokowany przez 5-60s indeksacji - timeout, gorsze UX
- Couplig: `document/` musi znać `indexing/` (przeciwko hexagonalu)
- Bez retry - jeden crash = utrata dokumentu

**Event (nasz wybór):**

Plusy:
- Faza 1 ~200ms, klient szybko dostaje response
- Decoupling - `document/` publikuje, `indexing/` słucha, nie wiedzą o sobie
- Retry przez Modulith outbox + republish on restart
- Łatwa extensja - dorzucenie `analytics/` listenera = jedna klasa, zero zmian w `document/`

### Dlaczego `@Transactional` na fazie 1?

Wszystko (file, document, event) musi być atomowe:
- Albo zapisane wszystko, albo nic
- Bez tego mogłaby się zdarzyć inconsistency: dokument w bazie ze statusem PENDING, ale event nie wysłany → dokument utknie

**Wyjątek:** zapis pliku na dysk - jak wyżej opisano, orphan files są tolerowane + GC.

### Dlaczego osobny `IndexingJob` w bazie?

Mógłbym używać samego pola `Document.status`. Ale `IndexingJob`:
- Trzyma historie prób (post-MVP retry)
- `errorMessage`, `chunksCreated`, `startedAt`, `completedAt` - bez "puchnięcia" `Document`
- Łatwiej monitorować "ile jobów aktualnie biegnie", "średni czas indeksacji"
- Separation of concerns: `Document` = byt biznesowy, `IndexingJob` = byt techniczny

---

## Frontend pattern - jak pokazać postęp

**Polling z exponential backoff:**

```javascript
async function pollDocumentStatus(documentId) {
  let delay = 500;
  while (true) {
    const { status } = await fetch(`/api/documents/${documentId}/status`).then(r => r.json());
    if (status === 'READY' || status === 'FAILED') return status;
    await sleep(delay);
    delay = Math.min(delay * 1.5, 5000);  // max 5s
  }
}
```

Wywoływane po POST /api/documents → response 202 → klient wie ID → pollLuje.

**Post-MVP:** zamiast pollingu - SSE / WebSocket dla notyfikacji push (bardziej responsywne UI, mniej zapytań).

---

## Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Flow chatu z RAG | [`chat-rag.md`](chat-rag.md) |
| Dlaczego Spring Modulith zamiast bezpośredniego @Async | [`../architecture/backend/events.md`](../architecture/backend/events.md) |
| Idempotency listenerów - szczegóły | [`../architecture/backend/events.md`](../architecture/backend/events.md) sekcja 9 |
| Schemat tabel `documents`, `stored_files`, `chunks`, `indexing_jobs` | [`../database/schema.md`](../database/schema.md) |
| Spec endpointu POST /api/documents | [`../api/openapi.yaml`](../api/openapi.yaml) |
| Moduły `document/`, `file/`, `indexing/`, `vector/` - co robią | [`../architecture/backend/modules-business.md`](../architecture/backend/modules-business.md) |
| Multi-tenancy w pipeline | [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md) |
