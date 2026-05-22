# Multibot Backend

Spring Boot 3 + Java 21 + Spring AI + pgvector. Modular monolith z hexagonalną architekturą per moduł.

Pełna dokumentacja architektoniczna w [`../docs/`](../docs/).

---

## Szybki start

### Wymagania
- **Java 21** (`java -version`)
- **Maven 3.9+** (`./mvnw -v` używa wrappera)
- **Docker** + **Docker Compose** (dla PostgreSQL + pgvector)
- Klucze API do **Anthropic** i **OpenAI**

### Setup

```bash
# 1. Z katalogu multibot/ - skopiuj .env.example
cd ..
cp .env.example .env
# Edytuj .env - wpisz ANTHROPIC_API_KEY i OPENAI_API_KEY

# 2. Uruchom Postgres + pgvector
docker-compose up -d
docker-compose ps   # sprawdź że postgres jest "healthy"

# 3. Uruchom backend
cd multibot-backend
./mvnw spring-boot:run
```

Albo z IntelliJ: otwórz `pom.xml` jako projekt → Run `MultibotApplication`.

### Sprawdzenie

```bash
# Health check
curl http://localhost:8080/actuator/health
# Oczekiwane: {"status":"UP"}

# Swagger UI (jeśli profile=dev)
open http://localhost:8080/swagger-ui.html
```

---

## Środowiska

| Profil | Aktywacja | Co robi |
|--------|-----------|---------|
| `dev` | default | Plain logs, SQL w konsoli, Swagger UI enabled, mock tenant |
| `test` | `mvn test` | Testcontainers Postgres, mock LLM, silent logs |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | JSON logs, Swagger off, real auth (post-MVP) |

Zmiana profilu: `SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run`.

---

## Struktura projektu

```
multibot-backend/
├── pom.xml                          # Maven config + dependencies
├── src/
│   ├── main/
│   │   ├── java/com/multibot/
│   │   │   ├── MultibotApplication.java
│   │   │   ├── shared/              # shared kernel (do utworzenia gdy implementujemy)
│   │   │   ├── tenancy/             # multi-tenancy infrastructure
│   │   │   ├── config/              # Spring @Configuration
│   │   │   ├── ai/                  # Spring AI beans
│   │   │   ├── tenant/              # business module
│   │   │   ├── file/                # business module
│   │   │   ├── vector/              # business module
│   │   │   ├── document/            # business module
│   │   │   ├── indexing/            # orchestrator module
│   │   │   ├── chat/                # business module
│   │   │   └── rag/                 # orchestrator module
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       └── db/migration/        # Flyway migracje per moduł
│   └── test/
│       └── java/com/multibot/
│           └── ApplicationModulesTest.java  # enforce granic modułów
```

Szczegóły organizacji modułów → [`../docs/architecture/backend/`](../docs/architecture/backend/).

---

## Zależności

Główne biblioteki (pełna lista w `pom.xml`):

| Stack | Co | Wersja |
|-------|-----|--------|
| **Spring Boot** | core framework | 3.4.1 |
| **Spring AI** | LLM (Claude) + embeddingi (OpenAI) + pgvector | 1.0.0 |
| **Spring Modulith** | module boundaries + transactional events | 1.3.1 |
| **PostgreSQL + pgvector** | baza + vector store | image: pg16 |
| **Flyway** | migracje DB | (Spring Boot BOM) |
| **Apache Tika** | ekstrakcja tekstu z PDF/DOCX/TXT | 2.9.2 |
| **java-uuid-generator** | UUID v7 (time-sortable) | 5.1.0 |
| **SpringDoc OpenAPI** | Swagger UI + walidacja spec | 2.6.0 |
| **openapi-generator-maven-plugin** | gen DTOs + API interfaces z `docs/api/openapi.yaml` | 7.10.0 |
| **Testcontainers** | testy integracyjne z prawdziwym Postgres | (Spring Boot BOM) |

---

## Pomocne komendy

```bash
# Build (bez testów)
./mvnw clean package -DskipTests

# Tylko kompilacja + testy
./mvnw test

# Generate OpenAPI code (z docs/api/openapi.yaml)
./mvnw generate-sources

# Sprawdź granice modułów
./mvnw test -Dtest=ApplicationModulesTest

# Czyste docker-compose (wyczyszczenie volume)
cd .. && docker-compose down -v && docker-compose up -d
```

---

## Następne kroki

Po pierwszym uruchomieniu (powinien wstać bez błędów):
1. Implementacja modułu `shared/` + `tenancy/` (fundament dla wszystkich innych)
2. Implementacja modułu `tenant/` (seed default tenanta)
3. Moduł `file/` (storage)
4. Moduł `document/` + `indexing/` (upload + RAG indeksacja)
5. Moduł `vector/`
6. Moduł `chat/` + `rag/` (SSE streaming odpowiedzi)

Każdy krok = ~jedna iteracja: design (jeśli trzeba doprecyzować) → kod → testy → integracja.

---

## Linki

- Dokumentacja architektury: [`../docs/`](../docs/)
- API spec: [`../docs/api/openapi.yaml`](../docs/api/openapi.yaml)
- Flow uploadu: [`../docs/flows/document-upload.md`](../docs/flows/document-upload.md)
- Flow chatu RAG: [`../docs/flows/chat-rag.md`](../docs/flows/chat-rag.md)
