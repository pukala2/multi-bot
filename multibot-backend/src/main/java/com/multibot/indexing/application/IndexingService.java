package com.multibot.indexing.application;

import com.multibot.document.domain.model.Document;
import com.multibot.document.domain.model.DocumentId;
import com.multibot.document.domain.model.DocumentStatus;
import com.multibot.document.domain.port.in.GetDocumentUseCase;
import com.multibot.file.domain.model.FileContent;
import com.multibot.file.domain.port.in.ReadFileUseCase;
import com.multibot.indexing.domain.event.IndexingCompletedEvent;
import com.multibot.indexing.domain.event.IndexingFailedEvent;
import com.multibot.indexing.domain.model.ExtractedText;
import com.multibot.indexing.domain.model.IndexingJob;
import com.multibot.indexing.domain.model.IndexingJobId;
import com.multibot.indexing.domain.port.in.IndexDocumentUseCase;
import com.multibot.indexing.domain.port.out.EmbeddingProvider;
import com.multibot.indexing.domain.port.out.IndexingJobRepository;
import com.multibot.indexing.domain.port.out.TextExtractor;
import com.multibot.shared.tenant.TenantId;
import com.multibot.vector.domain.model.Chunk;
import com.multibot.vector.domain.model.ChunkId;
import com.multibot.vector.domain.model.ChunkMetadata;
import com.multibot.vector.domain.port.in.DeleteChunksByDocumentUseCase;
import com.multibot.vector.domain.port.in.StoreChunksUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
class IndexingService implements IndexDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(IndexingService.class);

    private final GetDocumentUseCase getDocumentUseCase;
    private final ReadFileUseCase readFileUseCase;
    private final TextExtractor textExtractor;
    private final ChunkingPolicy chunkingPolicy;
    private final EmbeddingProvider embeddingProvider;
    private final StoreChunksUseCase storeChunksUseCase;
    private final DeleteChunksByDocumentUseCase deleteChunksUseCase;
    private final IndexingJobRepository jobRepository;
    private final com.multibot.document.domain.port.in.UpdateDocumentStatusUseCase updateDocumentStatus;
    private final ApplicationEventPublisher events;

    IndexingService(GetDocumentUseCase getDocumentUseCase,
                    ReadFileUseCase readFileUseCase,
                    TextExtractor textExtractor,
                    ChunkingPolicy chunkingPolicy,
                    EmbeddingProvider embeddingProvider,
                    StoreChunksUseCase storeChunksUseCase,
                    DeleteChunksByDocumentUseCase deleteChunksUseCase,
                    IndexingJobRepository jobRepository,
                    com.multibot.document.domain.port.in.UpdateDocumentStatusUseCase updateDocumentStatus,
                    ApplicationEventPublisher events) {
        this.getDocumentUseCase = getDocumentUseCase;
        this.readFileUseCase = readFileUseCase;
        this.textExtractor = textExtractor;
        this.chunkingPolicy = chunkingPolicy;
        this.embeddingProvider = embeddingProvider;
        this.storeChunksUseCase = storeChunksUseCase;
        this.deleteChunksUseCase = deleteChunksUseCase;
        this.jobRepository = jobRepository;
        this.updateDocumentStatus = updateDocumentStatus;
        this.events = events;
    }

    @Override
    public void index(DocumentId documentId) {
        Document document = getDocumentUseCase.getById(documentId);

        if (document.status() == DocumentStatus.READY) {
            log.info("Document {} already indexed (status=READY), skipping", documentId);
            return;
        }

        cleanupPreviousAttempt(documentId);
        updateDocumentStatus.markAsProcessing(documentId);

        Instant startedAt = Instant.now();
        IndexingJob job = jobRepository.save(IndexingJob.startedNow(
                IndexingJobId.generate(), document.tenantId(), documentId, startedAt));

        try {
            int chunksCreated = runPipeline(document);
            Instant completedAt = Instant.now();

            jobRepository.save(job.completed(chunksCreated, completedAt));
            updateDocumentStatus.markAsReady(documentId, completedAt);
            events.publishEvent(new IndexingCompletedEvent(
                    documentId, document.tenantId(), chunksCreated, completedAt));

            log.info("Document {} indexed: chunks={}, duration={}ms",
                    documentId, chunksCreated, completedAt.toEpochMilli() - startedAt.toEpochMilli());
        } catch (RuntimeException e) {
            Instant failedAt = Instant.now();
            jobRepository.save(job.failed(e.getMessage(), failedAt));
            updateDocumentStatus.markAsFailed(documentId, e.getMessage());
            events.publishEvent(new IndexingFailedEvent(
                    documentId, document.tenantId(), e.getMessage(), failedAt));

            log.error("Document {} indexing failed: {}", documentId, e.getMessage(), e);
        }
    }

    private void cleanupPreviousAttempt(DocumentId documentId) {
        deleteChunksUseCase.deleteByDocument(documentId.value());
        jobRepository.deleteByDocumentId(documentId);
    }

    private int runPipeline(Document document) {
        FileContent fileContent = readFileUseCase.read(document.storedFileId());

        ExtractedText extracted = textExtractor.extract(
                fileContent.bytes(), document.mimeType(), document.filename());
        if (extracted.isEmpty()) {
            throw new IllegalStateException(
                    "No extractable text in document " + document.filename()
                            + " (detected: " + extracted.detectedMimeType() + ")");
        }

        List<String> chunkTexts = chunkingPolicy.split(extracted.content());
        if (chunkTexts.isEmpty()) {
            throw new IllegalStateException("Chunking produced no chunks for document " + document.filename());
        }

        TenantId tenant = document.tenantId();
        Instant now = Instant.now();
        List<Chunk> chunks = buildChunks(document, chunkTexts, tenant, now);

        storeChunksUseCase.storeAll(chunks);
        return chunks.size();
    }

    private List<Chunk> buildChunks(Document document, List<String> chunkTexts, TenantId tenant, Instant now) {
        return java.util.stream.IntStream.range(0, chunkTexts.size())
                .mapToObj(i -> new Chunk(
                        ChunkId.generate(),
                        tenant,
                        document.id().value(),
                        i,
                        chunkTexts.get(i),
                        embeddingProvider.embed(chunkTexts.get(i)),
                        ChunkMetadata.of(Map.of("sourceFilename", document.filename())),
                        now))
                .toList();
    }
}
