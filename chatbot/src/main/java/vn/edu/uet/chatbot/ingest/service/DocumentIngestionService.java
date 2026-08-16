package vn.edu.uet.chatbot.ingest.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.uet.chatbot.chunk.ChunkSegment;
import vn.edu.uet.chatbot.chunk.TextChunker;
import vn.edu.uet.chatbot.config.RagProperties;
import vn.edu.uet.chatbot.ingest.extractor.DocumentTextExtractor;
import vn.edu.uet.chatbot.ingest.model.DocumentChunk;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionJob;
import vn.edu.uet.chatbot.ingest.model.DocumentPageText;
import vn.edu.uet.chatbot.store.VectorStore;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentTextExtractor documentTextExtractor;
    private final TextChunker textChunker;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final DocumentIngestionRegistry ingestionRegistry;
    private final DocumentStorageService documentStorageService;
    private final RagProperties ragProperties;

    @Async
    public CompletableFuture<Void> ingestPdfAsync(File file, String title) {
        return ingestPdfAsync(file, java.util.UUID.randomUUID().toString(), title, null);
    }

    @Async
    public CompletableFuture<Void> ingestPdfAsync(File file, String documentId, String title, String originalFilename) {
        return ingestPdfAsync(file, documentId, title, originalFilename, false);
    }

    @Async
    public CompletableFuture<Void> ingestPdfAsync(
            File file,
            String documentId,
            String title,
            String originalFilename,
            boolean deleteAfterIngest) {
        DocumentIngestionJob job = ingestionRegistry.findById(documentId)
                .orElseGet(() -> ingestionRegistry.createPending(documentId, title, originalFilename));

        try {
            ingestionRegistry.markProcessing(documentId);
            log.info(
                    "Ingest started documentId={} title={} source={} status={}",
                    documentId,
                    title,
                    originalFilename == null || originalFilename.isBlank() ? title : originalFilename,
                    job.status());
            ingestPdf(file, documentId, title, originalFilename);
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            ingestionRegistry.markFailed(documentId, message);
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        } finally {
            if (deleteAfterIngest && file != null && file.exists() && !file.delete()) {
                log.warn("Failed to delete temp uploaded file: {}", file.getAbsolutePath());
            }
        }
    }

    @Async
    public CompletableFuture<Void> reindexDocument(String documentId) {
        DocumentIngestionJob job = ingestionRegistry.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document job not found: " + documentId));

        if (job.storedFilePath() == null || job.storedFilePath().isBlank()) {
            throw new IllegalArgumentException("Stored source file is missing for document: " + documentId);
        }
        if (!documentStorageService.exists(job.storedFilePath())) {
            throw new IllegalArgumentException("File not found on storage: " + job.storedFilePath());
        }

        vectorStore.deleteByDocumentId(documentId);
        File tempFile = null;
        try {
            tempFile = documentStorageService.downloadToTempFile(job.storedFilePath());
            return ingestPdfAsync(tempFile, documentId, job.title(), job.originalFilename(), true);
        } catch (IOException ex) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    @Async
    public CompletableFuture<Void> ingestDocumentAsync(
            String documentId,
            String title,
            String originalFilename,
            String storedObjectPath) {
        DocumentIngestionJob job = ingestionRegistry.findById(documentId)
                .orElseGet(() -> ingestionRegistry.createPending(documentId, title, originalFilename));

        File tempFile = null;
        try {
            ingestionRegistry.markProcessing(documentId);
            log.info("Bắt đầu ingest từ MinIO: documentId={}, title={}, object={}", documentId, title,
                    storedObjectPath);
            tempFile = documentStorageService.downloadToTempFile(storedObjectPath);
            ingestPdf(tempFile, documentId, title, originalFilename);
            return CompletableFuture.completedFuture(null);
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            ingestionRegistry.markFailed(documentId, message);
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public void ingestPdf(File file, String documentId, String title, String originalFilename) throws IOException {
        StopWatch stopWatch = new StopWatch("ingest-" + documentId);
        DocumentIngestionJob myJobDetailFromDb = ingestionRegistry.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Rác database rồi =()="));

        stopWatch.start("extract");
        List<DocumentPageText> pages = documentTextExtractor.extractPages(file);
        stopWatch.stop();
        log.info("Ingest step=extract documentId={} durationMs={} pageCount={}", documentId,
                stopWatch.getLastTaskTimeMillis(), pages.size());

        stopWatch.start("chunk");
        List<ChunkSegment> chunkSegments = new ArrayList<>();
        for (DocumentPageText page : pages) {
            chunkSegments.addAll(textChunker.chunk(page.content(), page.pageNumber()));
        }
        stopWatch.stop();
        log.info("Ingest step=chunking documentId={} durationMs={} chunkCount={}", documentId,
                stopWatch.getLastTaskTimeMillis(), chunkSegments.size());
        if (!chunkSegments.isEmpty()) {
            try {
                ingestionRegistry.updateChunkCount(documentId, chunkSegments.size());
            } catch (Exception ex) {
                log.warn("Failed to update chunkCount intermediate for {}: {}", documentId, ex.getMessage());
            }
        }
        String source = originalFilename == null || originalFilename.isBlank() ? title : originalFilename;
        if (chunkSegments.isEmpty()) {
            ingestionRegistry.markDone(documentId, 0);
            log.info(
                    "Ingest completed documentId={} title={} source={} chunks=0 timings={}",
                    documentId,
                    title,
                    source,
                    stopWatch.prettyPrint());
            return;
        }

        List<DocumentChunk> chunkModels = new ArrayList<>();
        List<List<Double>> vectors = new ArrayList<>();

        String uploadedAt = java.time.Instant.now().toString();
        Map<String, Object> baseMetadata = new LinkedHashMap<>();
        baseMetadata.put("title", title);
        baseMetadata.put("documentId", documentId);
        baseMetadata.put("source", source);
        baseMetadata.put("originalFilename", originalFilename == null ? "" : originalFilename);
        baseMetadata.put("uploadedAt", uploadedAt);
        baseMetadata.put("owner", myJobDetailFromDb.owner() != null ? myJobDetailFromDb.owner() : "UNKNOWN");
        baseMetadata.put("isPublic", myJobDetailFromDb.isPublic());
        baseMetadata.put("category", myJobDetailFromDb.category().name());

        // --- CẢI TIẾN HIỆU NĂNG: nhúng theo lô (batch) thay vì gọi Ollama tuần tự
        // từng chunk một. Trước đây với N chunk sẽ có N request HTTP riêng biệt tới
        // Ollama; giờ gom thành các lô kích thước embeddingBatchSize để giảm số lần
        // round-trip mạng, nhanh hơn đáng kể với tài liệu nhiều trang.
        stopWatch.start("embed");
        int batchSize = Math.max(1, ragProperties.getEmbeddingBatchSize());
        List<String> chunkTexts = chunkSegments.stream().map(ChunkSegment::content).toList();
        for (int start = 0; start < chunkTexts.size(); start += batchSize) {
            int end = Math.min(start + batchSize, chunkTexts.size());
            List<float[]> batchVectors = embeddingModel.embed(chunkTexts.subList(start, end));
            for (float[] vector : batchVectors) {
                vectors.add(toDoubleList(vector));
            }
        }

        for (int i = 0; i < chunkSegments.size(); i++) {
            ChunkSegment segment = chunkSegments.get(i);
            DocumentChunk chunk = new DocumentChunk(
                    documentId,
                    i,
                    segment.pageNumber(),
                    segment.content(),
                    new LinkedHashMap<>(baseMetadata));
            chunkModels.add(chunk);
        }
        stopWatch.stop();
        log.info("Ingest step=embedding documentId={} durationMs={} chunkCount={} batchSize={}", documentId,
                stopWatch.getLastTaskTimeMillis(), chunkModels.size(), batchSize);

        stopWatch.start("upsert");
        vectorStore.upsert(documentId, chunkModels, vectors);
        stopWatch.stop();
        log.info("Ingest step=upsert_qdrant documentId={} durationMs={} chunkCount={}", documentId,
                stopWatch.getLastTaskTimeMillis(), chunkModels.size());

        ingestionRegistry.markDone(documentId, chunkModels.size());
        log.info("Ingest step=total documentId={} durationMs={}", documentId, stopWatch.getTotalTimeMillis());
        log.info(
                "Ingest completed documentId={} title={} source={} chunks={} timings={}",
                documentId,
                title,
                source,
                chunkModels.size(),
                stopWatch.prettyPrint());
    }

    private List<Double> toDoubleList(float[] vector) {
        List<Double> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add((double) value);
        }
        return result;
    }
}
