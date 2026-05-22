# Flow: Chat z RAG (Retrieval Augmented Generation)

Pełen flow od wiadomości End usera do wystreamowanej odpowiedzi bota z cytowaniem źródeł.

**Diagram:** [`../diagrams/chat-rag-flow.puml`](../diagrams/chat-rag-flow.puml)

---

## Cztery fazy

| Faza | Czas | Charakter | Co się dzieje |
|------|------|-----------|---------------|
| **1. Odebranie wiadomości** | ~50-100ms | Synchroniczny (HTTP) | Zapis user message + pobranie historii |
| **2. RAG retrieval** | ~200-500ms | Synchroniczny (pre-stream) | Embedding pytania + search w pgvector + budowa promptu |
| **3. SSE streaming** | ~2-10s | Streaming | LLM generuje, token po tokenie leci do klienta |
| **4. Cleanup** | ~50ms | Synchroniczny (post-stream) | Zapis assistant message + update last_activity |

End user widzi odpowiedź "wpisywaną" w czasie rzeczywistym dzięki fazy 3.

---

## Faza 1: Odebranie wiadomości

### Krok 1: HTTP request

```
POST /api/chat/sessions/{sessionId}/messages
Content-Type: application/json

{ "content": "Jakie są godziny otwarcia waszego biura?" }
```

### Krok 2: Walidacja

`@Valid` na `SendMessageRequest`:
- `content` not blank, max 4000 chars
- `sessionId` (path) format UUID

### Krok 3: Pobranie sesji

```sql
SELECT * FROM chat_sessions WHERE id=? AND tenant_id=?
```

(`tenant_id` filter automatyczny przez Hibernate `@TenantId`)

Jeśli sesja nie istnieje → 404.

### Krok 4: Zapis user message

```sql
INSERT INTO messages (id, tenant_id, session_id, role, content, created_at)
VALUES (?, ?, ?, 'USER', ?, NOW())
```

### Krok 5: Pobranie historii

Ostatnie N (default 10) wiadomości z tej sesji:
```sql
SELECT * FROM messages 
WHERE session_id=? AND tenant_id=? 
ORDER BY created_at DESC LIMIT 10
```

Limit konfigurowalny przez `multibot.chat.memory.window-size`.

**Po Phase 1 - COMMIT transakcji.** User message zapisana zanim zaczniemy długo trwający RAG + LLM call.

---

## Faza 2: RAG retrieval

`RagService.answerQuestion(RagQuery(question, history, tenantId))` zwraca `Flux<RagAnswerChunk>` (reactive stream).

### Krok 1: Embedding pytania

```
EmbeddingModel.embed(question) → float[1536]
```

- Spring AI call do OpenAI `/v1/embeddings`
- ~100-200ms latency
- Koszt: ~$0.000002 per query (zaniedbywalny)

### Krok 2: Search w pgvector

```
vector.SearchChunksUseCase(VectorSearchQuery(
    queryEmbedding,
    topK = 5
))
```

W `VectorService`:
1. Dodaje filter `tenant_id == TenantContext.get()`
2. Pobiera **50 kandydatów** z pgvector (workaround pre/post-filter problem - patrz [`../database/pgvector.md`](../database/pgvector.md) sekcja 8)
3. Aplikacyjny filter: `similarity > min_score` (default 0.7)
4. Zwraca top-K (5)

```sql
SELECT id, content, document_id, 1 - (embedding <=> $1) AS similarity
FROM chunks
WHERE tenant_id = $2
ORDER BY embedding <=> $1
LIMIT 50;
-- App-side filter: similarity > 0.7, top 5
```

Wynik: `List<VectorSearchResult>` z 0-5 chunkami.

**Edge case:** 0 wyników (wszystko poniżej threshold) - prompt zawiera notatkę "brak relevantnego kontekstu". LLM odpowiada "Nie znalazłem odpowiedzi w dostępnych dokumentach". Patrz Phase 3 → Edge cases.

### Krok 3: Budowa promptu

`PromptBuilder.build(...)` składa:

```
[SYSTEM PROMPT]
Jesteś asystentem firmy {tenant_name}. Odpowiadaj WYŁĄCZNIE 
na podstawie poniższego kontekstu z dokumentów firmowych.
Jeśli kontekst nie zawiera odpowiedzi - powiedz "Nie wiem".
Nie wymyślaj informacji.

[KONTEKST]
[Źródło 1: owu-pzu-2025.pdf, fragment]
...treść chunka 1...

[Źródło 2: cennik.pdf, fragment]  
...treść chunka 2...

[HISTORIA ROZMOWY]
User: pytanie sprzed 2 minut
Assistant: poprzednia odpowiedź
...

[AKTUALNE PYTANIE]
Jakie są godziny otwarcia waszego biura?
```

System prompt z `multibot.rag.system-prompt` (kopiowane z `insurance-assistant`).

---

## Faza 3: SSE streaming

### Krok 1: HTTP headers wysłane

Zaraz po fazach 1-2, kontroler ustala `Content-Type: text/event-stream` i nagłówki SSE. Klient już wie że to stream, otwiera EventSource.

### Krok 2: Call do LLM ze streamingiem

```
chatClient.prompt(prompt).stream() → Flux<String>
```

Spring AI woła Anthropic `POST /v1/messages?stream=true`. Anthropic streamuje tokeny.

### Krok 3: Mapowanie na SSE eventy

Dla każdego tokena/chunka tekstu z LLM:

```
data: {"text": "Tak, "}\n\n
data: {"text": "godziny otwarcia "}\n\n
data: {"text": "naszego biura to..."}\n\n
```

Format SSE:
```
event: chunk
data: {"text": "..."}

event: chunk
data: {"text": "..."}

event: sources
data: {"sources": [{"chunkId": "...", "documentId": "...", "snippet": "...", "score": 0.87}]}

event: done
data: {"messageId": "uuid", "tokensUsed": 1234}
```

### Krok 4: Klient renderuje na bieżąco

JavaScript end usera (widget / hosted page):
```javascript
const eventSource = new EventSource(`/api/chat/sessions/${id}/messages`);

eventSource.addEventListener('chunk', e => {
    const { text } = JSON.parse(e.data);
    appendToMessage(text);  // dopisuje do wiadomości w UI
});

eventSource.addEventListener('sources', e => {
    const { sources } = JSON.parse(e.data);
    renderCitations(sources);  // pokazuje "Źródła: dok1, dok2"
});

eventSource.addEventListener('done', e => {
    const { messageId } = JSON.parse(e.data);
    eventSource.close();
});
```

### Krok 5: Po zakończeniu streamu z LLM

- RagService składa pełną odpowiedź ze wszystkich delta
- Buduje `List<Citation>` z `retrievedChunks` (te które trafiły do promptu)
- Zwraca `RagAnswer(fullText, citations, tokensUsed)` do ChatService
- ChatService wysyła ostatnie SSE eventy: `sources` + `done`

---

## Faza 4: Zapis assistant message + cleanup

**Nowa `@Transactional`** (po streamingu - dlaczego osobno: patrz Decyzje niżej):

### Krok 1: Zapis pełnej odpowiedzi

```sql
INSERT INTO messages (
    id, tenant_id, session_id, role, content, sources, tokens_used, created_at
) VALUES (
    ?, ?, ?, 'ASSISTANT', ?, ?::jsonb, ?, NOW()
)
```

### Krok 2: Update aktywności sesji

```sql
UPDATE chat_sessions SET last_activity_at = NOW() WHERE id=?
```

Po-MVP: dorzucone `events.publishEvent(MessageSentEvent)` dla analytics.

---

## Obsługa błędów

### Faza 1 (przed streamingiem)

| Błąd | HTTP | Co dalej |
|------|------|----------|
| Walidacja content | 400 | Brak side effects |
| Session not found | 404 | jw. |
| DB write fail | 500 | Rollback, user message nie zapisana |

### Faza 2 (RAG)

| Błąd | Co dalej |
|------|----------|
| OpenAI embedding 429 | Retry z exponential backoff (3 próby), potem 500 |
| Pgvector query fail | 500 |
| 0 wyników (wszystko poniżej threshold) | Prompt z notatką "brak kontekstu" → LLM odpowiada "nie wiem" |

### Faza 3 (streaming)

**Tu jest najtrudniej.** Headers już wysłane (200 OK), nie możemy zmienić HTTP statusu. Strategie:

| Błąd | Co dalej |
|------|----------|
| Anthropic 429 / 5xx | Emitujemy SSE event `error` z opisem, klient pokazuje "Spróbuj ponownie" |
| Anthropic timeout | jw. |
| Crash app w środku | Połączenie zerwane, klient widzi błąd. **User message ZOSTAJE zapisana** (Phase 1 commited), ale nie ma assistant response. Klient widzi to w historii. |
| Anthropic odpowiada śmieciem (np. SQL injection w kontekście) | Już zapisane - polityka detekcji w post-MVP (content moderation) |

Format SSE error event:
```
event: error
data: {"message": "Wystąpił błąd, spróbuj ponownie.", "code": "LLM_ERROR"}
```

### Faza 4

| Błąd | Co dalej |
|------|----------|
| DB write fail przy zapisie assistant message | Loguj alert. Klient ma już odpowiedź (z streamingu) ale historia jej nie zawiera. Inconsistency między tym co widzi user a co jest w bazie. Akceptujemy w MVP - bardzo rzadkie, post-MVP można rozważyć retry. |

---

## Decyzje architektoniczne udokumentowane

### Dlaczego dwie osobne `@Transactional` (faza 1 + faza 4)?

**Naiwna implementacja:**
```
@Transactional
sendMessage() {
    saveUserMessage();
    history = loadHistory();
    answer = ragService.answer(...);  // ← 5-10 SEKUND
    saveAssistantMessage(answer);
}
```

Problemy:
- **Transakcja trzyma connection do bazy przez całe LLM call** - 5-10s z otwartym connection = wyczerpanie connection poola przy paru concurrent userach
- Brak rollback dla "częściowej odpowiedzi" - jeśli LLM padł w środku, transakcja rollback i user message też znika. To gorzej niż "user message zapisana, assistant brak".

**Nasz wzorzec:**
```
@Transactional
phase1() { save user message + load history }  // ~50ms, krótka transakcja

stream = ragService.stream(...)  // bez transakcji, długi I/O

@Transactional  
phase4() { save assistant message + update activity }  // ~50ms
```

Plusy: krótkie transakcje, connection pool zdrowy, częściowa odporność na crash.

### Dlaczego SSE a nie WebSocket?

| Aspekt | SSE | WebSocket |
|--------|-----|-----------|
| Kierunek | tylko server → client | dwukierunkowy |
| Setup | proste, standardowy HTTP | wymaga handshake + dedicated protocol |
| Reconnect | automatyczny w EventSource API | trzeba samemu |
| Wsparcie proxy / CDN | dobre (zwykły HTTP) | gorsze (niektóre proxy nie wspierają) |
| Format | text events | binary or text frames |

Dla naszego use case: **streaming odpowiedzi to wyłącznie server → client**, nie potrzebujemy dwukierunkowości. SSE wygrywa prostotą.

WebSocket warto rozważyć gdy:
- Klient też wysyła ciąg eventów (typing indicators, presence)
- Mamy wiele zdarzeń server-side oprócz odpowiedzi LLM

### Dlaczego N=10 wiadomości w historii (memory window)?

Trade-off:
- Mniej wiadomości → tańsze (krótszy prompt) + szybsze (mniej tokenów do LLM), ale traci kontekst dla długich rozmów
- Więcej wiadomości → bogatszy kontekst, ale dłuższy prompt = wyższy koszt i latency

10 jest praktycznie wystarczające dla wspólnego flow customer support (większość zapytań to 1-3 wymiany). Konfigurowalne przez property.

Post-MVP: summarization dla starszych wiadomości (Spring AI ma `MessageWindowChatMemory` z summarization).

### Dlaczego pobieramy 50 chunków i filtrujemy do 5 w aplikacji?

Patrz [`../database/pgvector.md`](../database/pgvector.md) sekcja 8 - post-filter problem z HNSW. Skrót: pgvector przy `WHERE tenant_id = X ORDER BY embedding LIMIT 5` może zwrócić mniej niż 5 wyników jeśli top-5 globalnie nie pasuje do filtra. Workaround: zwiększyć LIMIT, filtrować w aplikacji.

### Co z idempotency dla wysłania wiadomości?

End user kliknie "Send" dwa razy w 100ms - czy dostanie 2 user messages w historii?

**Tak, w MVP** - brak deduplikacji. Akceptowane:
- Realistyczne dla chat UX (jak każdy messenger)
- Frontend powinien disable button po kliknięciu

**Post-MVP:** idempotency key (klient generuje UUID, serwer wykrywa duplikat).

---

## Streaming i SSE - praktyczne notatki

### Spring implementation

Używamy `SseEmitter` ze Spring Web MVC (servlet-based):

```java
@PostMapping(value = "/api/chat/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter sendMessage(@PathVariable UUID id, @RequestBody SendMessageRequest req) {
    SseEmitter emitter = new SseEmitter(60_000L); // 60s timeout
    chatService.sendMessage(id, req.content(), emitter);
    return emitter;
}
```

ChatService dostaje `SseEmitter` i woła `emitter.send(event)` per chunk.

Alternatywa - WebFlux + `Flux<ServerSentEvent<>>` - bardziej reactive, ale zmiana stacku z Servlet na WebFlux. **MVP: Servlet + SseEmitter** (mniejsza zmiana).

### CORS dla widgetu

Widget JS będzie wysyłać z innej origin (`acme.com`) do naszego API (`api.multibot.app`). SSE działa z CORS, ale trzeba odpowiednie nagłówki:

```
Access-Control-Allow-Origin: https://acme.com
Access-Control-Allow-Credentials: true (jeśli chcemy cookies dla anonymousId)
```

Per-tenant CORS config: pobierany z `widget_keys.allowed_origins` (post-MVP).

### Timeouts

- HTTP request timeout (default Tomcat 60s)
- SseEmitter timeout (ustawiamy 60s)
- LLM call timeout (Spring AI default ~30s)

Jeśli LLM trwa >30s - przerwany, emitujemy error event. Realnie streaming zaczyna lecieć po ~1s, kończy po 5-10s.

---

## Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Flow uploadu dokumentu | [`document-upload.md`](document-upload.md) |
| Workaround dla pgvector post-filter problem | [`../database/pgvector.md`](../database/pgvector.md) sekcja 8 |
| Spec endpointu POST /messages + format SSE | [`../api/openapi.yaml`](../api/openapi.yaml) |
| Moduły `chat/`, `rag/`, `vector/`, `ai/` - co robią | [`../architecture/backend/modules-business.md`](../architecture/backend/modules-business.md) |
| Multi-tenancy w vector search | [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md) |
| Konfiguracja LLM, embedding model, top-K | [`../architecture/backend/operations.md`](../architecture/backend/operations.md) sekcja 1 |
