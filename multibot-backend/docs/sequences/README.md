# Multibot Backend — diagramy sekwencji

Wszystkie istotne przepływy w backendzie jako PlantUML. Każdy plik pokazuje co dzieje się
od momentu requesta klienta aż do zwrotu odpowiedzi (i ewentualnej pracy w tle).

## Mapa diagramów

### Documents

| # | Plik | Co opisuje |
|---|------|-----------|
| 01 | [01-upload-document.puml](01-upload-document.puml) | `POST /api/documents` — synchroniczna część (zapis + publikacja eventu) |
| 02 | [02-indexing-pipeline.puml](02-indexing-pipeline.puml) | **Background**: event → ekstrakcja Tika → chunkowanie → embeddingi OpenAI → pgvector |
| 03 | [03-list-documents.puml](03-list-documents.puml) | `GET /api/documents` — paginowana lista |
| 04 | [04-document-status-polling.puml](04-document-status-polling.puml) | `GET /api/documents/{id}/status` — polling z frontu |
| 05 | [05-delete-document.puml](05-delete-document.puml) | `DELETE /api/documents/{id}` — kaskadowe sprzątanie (chunks + plik + event) |

### Chat

| # | Plik | Co opisuje |
|---|------|-----------|
| 06 | [06-start-chat-session.puml](06-start-chat-session.puml) | `POST /api/chat/sessions` — utworzenie sesji |
| 07 | [07-send-message-blocking.puml](07-send-message-blocking.puml) | `POST messages` z `Accept: application/json` — RAG blocking |
| 08 | [08-send-message-sse.puml](08-send-message-sse.puml) | `POST messages` z `Accept: text/event-stream` — SSE streaming |
| 09 | [09-list-sessions.puml](09-list-sessions.puml) | `GET /api/chat/sessions` |
| 10 | [10-session-history.puml](10-session-history.puml) | `GET /api/chat/sessions/{id}/messages` |

### Admin / Widget

| # | Plik | Co opisuje |
|---|------|-----------|
| 11 | [11-admin-stats.puml](11-admin-stats.puml) | `GET /api/admin/stats` — agregacja metryk |
| 12 | [12-widget-admin-config.puml](12-widget-admin-config.puml) | `GET/PUT /api/admin/widget` |
| 13 | [13-widget-public-config.puml](13-widget-public-config.puml) | `GET /api/widget/config?key=` (publiczny dla widgetu) |

### Cross-cutting

| # | Plik | Co opisuje |
|---|------|-----------|
| 14 | [14-multi-tenancy-flow.puml](14-multi-tenancy-flow.puml) | Filter chain → TenantContext → MDC → Hibernate `@TenantId` |
| 15 | [15-modulith-event-publication.puml](15-modulith-event-publication.puml) | Transactional outbox pattern (Spring Modulith) |

---

## Jak generować obrazki PNG/SVG

**Opcja 1 — VS Code plugin** (rekomendowane, instant preview):
1. Zainstaluj rozszerzenie **PlantUML** (jebbs.plantuml)
2. Otwórz dowolny `.puml`
3. `Alt+D` (Win/Linux) lub `Option+D` (Mac) → live preview

**Opcja 2 — IntelliJ IDEA**:
1. Plugin **PlantUML Integration**
2. Otwórz `.puml` → tabba preview po prawej

**Opcja 3 — CLI (batch dla wszystkich)**:
```bash
# Wymaga Javy + plantuml.jar
brew install plantuml          # macOS
plantuml docs/sequences/*.puml  # generuje PNG obok plików
```

**Opcja 4 — online**:
- Wklej zawartość pliku na https://www.plantuml.com/plantuml/uml/

## Konwencje używane w diagramach

- **`actor`** = klient (przeglądarka, widget, admin UI)
- **`participant Filter`** = chain filtrów Servlet/Spring
- **`participant Controller`** = warstwa REST (`*Controller.java`)
- **`participant Service`** = warstwa application (`*Service.java`)
- **`participant Repository`** = port out (interface) + adapter
- **`database "PostgreSQL"`** = persistencja
- **`participant "OpenAI"`** / **`"Anthropic"`** = zewnętrzne API
- **`note over X`** = komentarz wyjaśniający decyzję projektową
- **`alt`/`opt`/`loop`** = control flow (alternative/optional/loop)

## Jak czytać kolory tła (jeśli renderer wspiera)

W komentarzach używamy:
- `note over X #LightBlue` = info techniczne
- `note over X #LightYellow` = uwaga/edge case
- `note over X #LightPink` = błąd/wyjątek

## Powiązane

- [openapi.yaml](../../../docs/api/openapi.yaml) — kontrakt REST API
- [implementation-plan.md](../../implementation-plan.md) — plan implementacji backendu (8 faz)
- [docs/diagrams/](../../../docs/diagrams/) — diagramy architektury (komponenty, moduły)
