# Pgvector - vector store

Szczegóły wykorzystania pgvectora jako vector store dla embeddingów RAG. Schemat samej tabeli `chunks` opisany w [`schema.md`](schema.md) - tutaj koncentrujemy się na operacjach wektorowych, indeksach i query patterns.

---

## 1. Co to pgvector i dlaczego

**pgvector** to extension PostgreSQL dodający:
- Typ kolumny `vector(n)` - tablica n floatów
- Operatory distance: `<->` (L2), `<=>` (cosine), `<#>` (inner product)
- Indeksy `HNSW` i `IVFFlat` dla szybkiego ANN (Approximate Nearest Neighbor) search

### Czemu pgvector, nie Qdrant/Weaviate/Pinecone

Uzasadnienie wyboru:

- **Jedna baza dla danych relacyjnych + wektorów** - jeden backup, jeden monitoring, brak distributed transactions
- **Łatwy join wektorów z metadanymi** - "znajdź chunki z dokumentu X dla tenanta Y o similarity > 0.7" to jedno query
- **Spring AI ma natywne wsparcie** - `PgVectorStore` z auto-konfiguracją
- **Skalowalność wystarczająca** dla MVP i pierwszych klientów (do ~10M wektorów)

Trade-off: dla bardzo dużej skali (100M+ wektorów) dedicated vector DB (Qdrant) wyprzedza pgvector. Ale to długo nie nasz problem.

---

## 2. Setup i instalacja

### Wymagania
- PostgreSQL 15+ (preferowane 16 - lepsza wydajność HNSW)
- Extension `vector` zainstalowane

### Docker image

W `docker-compose.yml`:
```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: multibot
      POSTGRES_USER: multibot
      POSTGRES_PASSWORD: multibot
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
```

**Nie używamy** `postgres:16` - tam pgvector wymaga ręcznej instalacji. Image `pgvector/pgvector:pg16` ma to gotowe.

### Migracja Flyway

`V20250522_0001__create_extension_pgvector.sql`:
```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

**Wyjątkowo** używamy `IF NOT EXISTS` - bo extension może być już zainstalowane w bazie.

### Sprawdzenie

```sql
SELECT extversion FROM pg_extension WHERE extname = 'vector';
-- powinno zwrócić np. '0.7.4'
```

---

## 3. Typ `vector` i wymiary

### Deklaracja kolumny

```sql
embedding vector(1536) NOT NULL
```

`1536` to liczba wymiarów - musi być **dokładnie dopasowana** do modelu embeddingowego.

### Nasz wybór wymiaru

| Model | Wymiary | Decyzja |
|-------|---------|---------|
| OpenAI `text-embedding-3-small` | **1536** | ✅ MVP - tani, dobry stosunek jakości do ceny |
| OpenAI `text-embedding-3-large` | 3072 | opcja jeśli MVP pokaże że jakość RAG słaba |
| OpenAI `text-embedding-ada-002` (legacy) | 1536 | NIE - przestarzały |

### Co jeśli zmienimy model embeddingowy

Wymiary embeddingów **musi się zgadzać** - nie można porównywać 1536-wymiarowego wektora z 3072-wymiarowym.

Migracja w 3 krokach:
1. Nowa migracja Flyway: `ALTER TABLE chunks ALTER COLUMN embedding TYPE vector(3072)` - **wymaga TRUNCATE chunks albo recompute wszystkich**
2. Aplikacja w trybie maintenance: reindex wszystkich dokumentów (re-embedding)
3. Update property `multibot.ai.embedding.dimensions=3072`

**Lepsze:** nie zmieniać modelu po wdrożeniu w produkcji. Wybierać dobrze na starcie.

---

## 4. Distance metrics (similarity)

### Trzy operatory pgvectora

| Operator | Distance | Kiedy używać |
|----------|----------|--------------|
| `<->` | L2 (Euclidean) | gdy magnituda wektora ma znaczenie (rzadkie dla embeddingów) |
| `<=>` | Cosine distance | **standard dla embeddingów tekstu** - mierzy kąt między wektorami |
| `<#>` | Inner product (negated) | gdy embeddingi są pre-normalized (jak OpenAI) - matematycznie === cosine, ale szybsze |

### Nasz wybór: `<=>` (cosine distance)

Powody:
- OpenAI embeddingi są normalized (długość 1) - cosine jest naturalnym wyborem
- Stabilny i przewidywalny zakres
- Standardowa konwencja w branży

### Wartości

| `embedding <=> query` | Znaczenie |
|----------------------|-----------|
| 0.0 | wektory identyczne |
| 1.0 | wektory ortogonalne (niezwiązane) |
| 2.0 | wektory przeciwne |

**Similarity score** (im wyżej tym lepiej) - konwersja:
```sql
1 - (embedding <=> query) AS similarity_score
-- zakres: typowo [0, 1], wyższe = bardziej podobne
```

Threshold w RAG (z `multibot.rag.min-score`):
- `0.7+` - silnie podobne, dobre cytaty
- `0.5-0.7` - słabo podobne, ryzyko halucynacji
- `<0.5` - prawdopodobnie nierelevantne

Tuning thresholdu - jedna z rzeczy do testów po MVP.

### `<#>` jako optymalizacja (post-MVP)

Skoro OpenAI embeddingi są normalized:
```sql
-- Te dwa są matematycznie równe:
ORDER BY embedding <=> $1
ORDER BY embedding <#> $1  -- ~30% szybsze, ale tylko dla normalized vectors
```

Można rozważyć w produkcji jeśli profilowanie pokaże że to bottleneck. Trzymamy `<=>` jako default - prostsze do debugowania.

---

## 5. Index types - HNSW vs IVFFlat

### Porównanie

| Aspekt | HNSW | IVFFlat |
|--------|------|---------|
| **Build time** | Wolny (O(n log n) z dużą stałą) | Szybki (O(n)) |
| **Query time** | **Bardzo szybki** | Średni |
| **Recall (jakość)** | **Wysoki** (98%+) | Średni (90-95%) |
| **Memory** | Wyższe zużycie RAM | Mniejsze |
| **Reindex po insertach** | Nie wymaga | Wymaga jeśli `lists` źle dobrane do skali |
| **Build z pustej tabeli** | Działa OK | **Nie zalecane** (wymaga znanej liczby wektorów) |

### Nasz wybór: HNSW

Powody:
- **Read-heavy workload** - czytamy DUŻO częściej niż piszemy (1 indeksacja → tysiące query)
- **Wysoki recall krytyczny** dla RAG - złe cytaty = niezadowolony klient
- **Nie znamy z góry liczby wektorów** - HNSW radzi sobie z rosnącym datasetem bez reindex
- **Skala MVP-post-MVP** (do kilku milionów wektorów) - HNSW radzi sobie świetnie

### Deklaracja indexu

```sql
-- W migracji V20250522_0601__create_chunks_hnsw_index.sql
CREATE INDEX idx_chunks_embedding 
ON chunks 
USING HNSW (embedding vector_cosine_ops);
```

`vector_cosine_ops` - operator class dla cosine distance. **Musi pasować** do operatora używanego w query. Inne opcje: `vector_l2_ops`, `vector_ip_ops`.

---

## 6. Parametry HNSW

### Build-time parameters

W deklaracji indexu:
```sql
CREATE INDEX idx_chunks_embedding 
ON chunks 
USING HNSW (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);
```

| Parametr | Default | Co robi | Nasza wartość |
|----------|---------|---------|---------------|
| `m` | 16 | Liczba połączeń per node w grafie HNSW. Wyższe = lepszy recall + większy index | **16** (default) |
| `ef_construction` | 64 | Jakość budowania indexu. Wyższe = lepszy recall + wolniejszy build | **128** dla wyższego recall |

Decyzja:
- `m=16` - default jest OK na start
- `ef_construction=128` - poświęcamy 2x dłuższy build time za lepszy recall

### Query-time parameter

```sql
SET hnsw.ef_search = 100;
-- przed query
SELECT * FROM chunks ORDER BY embedding <=> $1 LIMIT 5;
```

| Parametr | Default | Co robi |
|----------|---------|---------|
| `ef_search` | 40 | Liczba kandydatów odwiedzanych podczas search. Wyższe = lepszy recall + wolniejsze query |

Tuning per query:
- `ef_search = 40` (default) - szybkie, recall ~95%
- `ef_search = 100` - wolniejsze ~2x, recall ~99%
- `ef_search = 200` - jeszcze wolniejsze, recall niemal idealny

Nasza propozycja: **`ef_search = 100`** w `application.yml`, do tuningu po pomiarach.

---

## 7. Query patterns

### Podstawowe wyszukiwanie

```sql
SELECT id, content, 1 - (embedding <=> $1) AS similarity
FROM chunks
ORDER BY embedding <=> $1
LIMIT 5;
```

`$1` to query embedding (1536 floatów). HNSW automatycznie używany przez optimizer.

### Z filtrem tenant_id (zawsze)

```sql
SELECT id, content, document_id, 1 - (embedding <=> $1) AS similarity
FROM chunks
WHERE tenant_id = $2
ORDER BY embedding <=> $1
LIMIT 5;
```

**Każde query w multibocie musi mieć ten filter.** Wymuszane przez `TenantAwareVectorStore` (moduł `vector/`).

### Z filtrem dokumentów

```sql
SELECT id, content, 1 - (embedding <=> $1) AS similarity
FROM chunks
WHERE tenant_id = $2 
  AND document_id = ANY($3::uuid[])
ORDER BY embedding <=> $1
LIMIT 5;
```

Użycie: gdy admin chce ograniczyć wyszukiwanie do konkretnych dokumentów (post-MVP feature).

### Z minimum score

```sql
SELECT id, content, 1 - (embedding <=> $1) AS similarity
FROM chunks
WHERE tenant_id = $2
  AND 1 - (embedding <=> $1) > 0.7
ORDER BY embedding <=> $1
LIMIT 5;
```

Filter na `similarity > 0.7` eliminuje słabe matche. Konfigurowalny przez `multibot.rag.min-score`.

### Z filtrem metadata (JSONB)

```sql
SELECT id, content
FROM chunks
WHERE tenant_id = $2
  AND metadata @> '{"section": "Cennik"}'::jsonb
ORDER BY embedding <=> $1
LIMIT 5;
```

Wymaga GIN index na `metadata` (mamy w schemacie).

---

## 8. Filtered search - pre-filter vs post-filter

To **najmniej intuicyjna** część pgvectora. Trzeba zrozumieć żeby uniknąć problemów.

### Domyślne zachowanie (post-filter)

Gdy piszesz:
```sql
WHERE tenant_id = X 
ORDER BY embedding <=> query 
LIMIT 5
```

Pgvector z HNSW domyślnie robi **post-filter**:
1. Najpierw szuka top-K najbardziej podobnych w **całym indexie** (~50 kandydatów)
2. Dopiero potem filtruje po `WHERE tenant_id = X`
3. Zwraca top 5 z tego co przeżyło filtr

### Problem

Co jeśli wśród top-50 globalnie nie ma ŻADNEGO chunka z tenantem X? Dostaniesz **mniej niż 5 wyników**, mimo że tenant X ma 1000 chunków w bazie.

Przykład realny: 
- 10 tenantów, każdy ma 1000 chunków
- Tenant Y ma chunk "perfekcyjnie pasujący" do query - similarity 0.99
- Tenant X ma najlepszy chunk z similarity 0.85
- Query `WHERE tenant_id = X` może zwrócić 0 wyników, bo HNSW nie zszedł wystarczająco głęboko

### Workaround dla MVP

**Zwiększyć `LIMIT` przed filtrem, potem brać top-5 w aplikacji:**

```sql
-- Pobierz top-50 (post-filter eliminuje większość)
SELECT id, content, 1 - (embedding <=> $1) AS similarity
FROM chunks
WHERE tenant_id = $2
ORDER BY embedding <=> $1
LIMIT 50;
```

Aplikacja bierze pierwsze 5 z wyniku. Trade-off: marnujemy trochę pracy, ale dostajemy stabilne wyniki.

W naszym kodzie:
- `multibot.rag.top-k = 5` (final liczba wyników do promptu)
- `multibot.rag.candidate-multiplier = 10` (mnożnik dla LIMIT w SQL = 50)

### Lepsze rozwiązanie (post-MVP) - partitioned tables

```sql
CREATE TABLE chunks (...) PARTITION BY HASH (tenant_id);
```

Pgvector tworzy osobny HNSW per partycja → query w obrębie partycji = brak problemu z post-filter. Wymaga `pg_partman` lub ręcznych partycji.

Decyzja: **odkładamy do post-MVP**. Trzeba pomierzyć czy realnie nas to ugryzie przy realnym ruchu.

### Iterative scan (pgvector 0.8+)

Najnowsza wersja pgvectora (0.8+) wprowadza `hnsw.iterative_scan = strict_order` - automatycznie pogłębia search jeśli filtr odrzuca za dużo wyników. **Idealne dla nas.** Sprawdzić wersję pgvectora w image `pgvector/pgvector:pg16` - jeśli ma 0.8+, włączamy.

---

## 9. Spring AI integration

### `PgVectorStore` bean

Spring AI automatycznie tworzy bean `VectorStore` (jako `PgVectorStore`) jeśli mamy:
- Dependency `spring-ai-pgvector-store-spring-boot-starter`
- DataSource skonfigurowany
- Property `spring.ai.vectorstore.pgvector.*`

### Konfiguracja w `application.yml`

```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536
        initialize-schema: false  # MY tworzymy schema przez Flyway, NIE Spring AI
```

**Krytyczne:** `initialize-schema: false`. Spring AI domyślnie próbuje stworzyć swoją tabelę `vector_store` - my tego NIE chcemy, używamy własnej `chunks` z naszym schematem.

### Custom tabela vs domyślna Spring AI

Spring AI domyślnie operuje na tabeli `vector_store(id, content, metadata, embedding)`. My mamy własną `chunks` z dodatkowymi kolumnami (`tenant_id`, `document_id`, `chunk_index`).

Opcje:
1. **Custom JdbcTemplate w naszym adapterze** - omijamy domyślny `PgVectorStore`, sami piszemy SQL. **Wybieramy to.** Kontrola, multi-tenancy filter pewny.
2. Używamy Spring AI VectorStore z domyślną tabelą + osobna tabela `chunk_metadata` z FK do `vector_store` - więcej JOIN-ów, więcej skomplikowane.

### Jak to wygląda w naszym module `vector/`

`PgVectorChunkStoreAdapter` (klasa w `vector/infrastructure/pgvector/`):
- Wstrzykuje `EmbeddingModel` (ze Spring AI) - do embedowania query
- Wstrzykuje `JdbcTemplate` - do customowych queries
- Implementuje `ChunkStore` (port OUT z `vector/domain/port/out/`)
- **Wymusza filtr `tenant_id`** z `TenantContext.get()` przy każdym search/store

W praktyce nie używamy bezpośrednio `org.springframework.ai.vectorstore.VectorStore` - mamy własną abstrakcję dopasowaną do multi-tenancy.

---

## 10. Performance i tuning

### Po wdrożeniu wszystkich migracji

```sql
VACUUM ANALYZE chunks;
```

Bez tego optimizer może wybierać złe plany. Robić **po każdej dużej batch insercji** (np. po indeksacji wielkiego dokumentu).

### `maintenance_work_mem` dla budowania indexu

```sql
SET maintenance_work_mem = '2GB';
CREATE INDEX ... USING HNSW ...;
```

HNSW build potrzebuje dużo RAM. Domyślne 64MB jest za mało dla większych datasetów - build trwa godziny. Zwiększyć przed `CREATE INDEX`.

W produkcji Postgres: `maintenance_work_mem` jako parameter sesji w `postgresql.conf`.

### Co monitorować

| Metryka | Jak | Threshold |
|---------|-----|-----------|
| Query latency | `EXPLAIN ANALYZE` typowych query | <50ms p95 dla 5 chunków |
| Index size | `pg_size_pretty(pg_relation_size('idx_chunks_embedding'))` | informacyjne |
| Table size | `pg_size_pretty(pg_relation_size('chunks'))` | jak rośnie - alert >10GB |
| Liczba chunków per tenant | `SELECT tenant_id, COUNT(*) FROM chunks GROUP BY tenant_id` | wykres trendu |
| Slow queries | `pg_stat_statements` | top 10 wolnych |

### Kiedy reindex

Pgvector HNSW **nie wymaga okresowego reindex** (w przeciwieństwie do IVFFlat). Reindex robimy tylko jeśli:
- Zmiana parametrów (`m`, `ef_construction`)
- Bardzo dużo deletes (powyżej ~20% rekordów) - VACUUM FULL + REINDEX
- Migracja na nowy wymiar embeddingów

---

## 11. Limitations i co dalej

### Hard limits pgvectora

| Limit | Wartość | Dotyczy nas? |
|-------|---------|--------------|
| Max wymiar dla HNSW | 2000 | Nie (mamy 1536) |
| Max wymiar dla `vector` typu | 16000 | Nie |
| Max wymiarów dla IVFFlat | 2000 | Nie używamy IVFFlat |

### Czego pgvector NIE robi (post-MVP rozważania)

1. **Hybrid search** (vector + BM25 fulltext) - daje lepsze wyniki dla queries z konkretną terminologią. Trzeba samemu łączyć pgvector search + Postgres `tsvector`.

2. **Reranking** - po retrieval, ponowna ocena chunków przez bardziej precyzyjny model (np. Cohere Rerank). Zewnętrzne API, do dodania osobno.

3. **Multi-vector queries** - "znajdź chunki podobne do query A LUB query B". Trzeba dwa search + dedup w aplikacji.

4. **Distributed sharding** - dla skali 100M+ wektorów. pgvector nie ma read replicas dedicated do vector search. Wtedy: Qdrant / Pinecone / Weaviate.

### Roadmap

| Etap | Co dorzucamy |
|------|--------------|
| **MVP** | Pgvector + HNSW + cosine. Basic top-K. Filter tenant_id. |
| **Post-MVP v1** | Iterative scan (jeśli pgvector 0.8+). Min score threshold. |
| **Post-MVP v2** | Reranker (Cohere lub własny). Tuning parametrów na podstawie metryk. |
| **Post-MVP v3** | Hybrid search (vector + BM25). Multi-vector queries. |
| **Skala 100M+** | Migracja na dedicated vector DB (Qdrant). |

---

## 12. Linki dalej

| Chcesz wiedzieć... | Idź do... |
|--------------------|-----------|
| Schemat tabeli `chunks` + indeksy | [`schema.md`](schema.md) |
| Strategia multi-tenancy w wyszukiwaniu (TenantAwareVectorStore) | [`../architecture/multi-tenancy.md`](../architecture/multi-tenancy.md) |
| Modul `vector/` - klasy, porty, adaptery | [`../architecture/backend/modules-business.md`](../architecture/backend/modules-business.md) |
| Modul `ai/` - Spring AI beans (EmbeddingModel) | [`../architecture/backend/modules-infrastructure.md`](../architecture/backend/modules-infrastructure.md) |
| Flow RAG (jak vector search wpina się do całości) | [`../flows/chat-rag.md`](../flows/chat-rag.md) |
