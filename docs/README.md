# Multibot - dokumentacja projektu

Platforma SaaS B2B do zarządzania chatbotami AI z RAG dla firm.

## Mapa dokumentacji

### Punkt startowy
- [brief.md](brief.md) - oryginalny brief biznesowo-techniczny

### `concept/` - CO i DLACZEGO
- [vision.md](concept/vision.md) - wizja, target, model biznesowy
- [mvp-scope.md](concept/mvp-scope.md) - zakres MVP, co odpuszczamy
- [use-cases.md](concept/use-cases.md) - user stories i przypadki użycia

### `architecture/` - JAK (wysokopoziomowo)
- [overview.md](architecture/overview.md) - diagram komponentów, jak wszystko ze sobą gada
- [multi-tenancy.md](architecture/multi-tenancy.md) - strategia izolacji tenantów
- `backend/` - dokumentacja backendu (rozbite na 6 plików):
  - [overview.md](architecture/backend/overview.md) - filozofia, drzewo pakietów, decyzje techniczne
  - [hexagonal-pattern.md](architecture/backend/hexagonal-pattern.md) - wzorzec hexagonalny per moduł
  - [modules-infrastructure.md](architecture/backend/modules-infrastructure.md) - shared, tenancy, config, ai
  - [modules-business.md](architecture/backend/modules-business.md) - tenant, user, file, vector, document, indexing, chat, rag
  - [events.md](architecture/backend/events.md) - Spring Modulith + eventy
  - [operations.md](architecture/backend/operations.md) - konfiguracja, błędy, logi, testy
- frontend.md - *(później)*
- widget.md - *(później)*

### `database/` - DANE
- [schema.md](database/schema.md) - ERD, tabele, indeksy, relacje
- [pgvector.md](database/pgvector.md) - strategia vector store

### `api/` - INTERFEJSY
- [endpoints.md](api/endpoints.md) - specyfikacja REST API

### `flows/` - SEKWENCJE
- [document-upload.md](flows/document-upload.md) - upload i indeksacja dokumentu
- [chat-rag.md](flows/chat-rag.md) - rozmowa z botem (RAG flow)
- *(kolejne dorzucamy iteracyjnie)*

---

## Stan dokumentów

| Dokument | Status |
|----------|--------|
| brief.md | gotowy |
| concept/* | do napisania |
| architecture/overview.md | gotowy ✅ |
| architecture/multi-tenancy.md | gotowy ✅ |
| architecture/backend/overview.md | gotowy ✅ |
| architecture/backend/hexagonal-pattern.md | gotowy ✅ |
| architecture/backend/modules-infrastructure.md | gotowy ✅ |
| architecture/backend/modules-business.md | gotowy ✅ |
| architecture/backend/events.md | gotowy ✅ |
| architecture/backend/operations.md | gotowy ✅ |
| database/schema.md | gotowy ✅ |
| database/pgvector.md | gotowy ✅ |
| api/endpoints.md | gotowy ✅ |
| api/openapi.yaml | gotowy ✅ |
| flows/document-upload.md | gotowy ✅ |
| flows/chat-rag.md | gotowy ✅ |
| concept/* | do napisania |
| database/* | do napisania |
| api/* | do napisania |
| flows/* | do napisania |
