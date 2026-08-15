package vn.edu.uet.chatbot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.uet.chatbot.dto.DocumentDeleteResponse;
import vn.edu.uet.chatbot.dto.DocumentReindexResponse;
import vn.edu.uet.chatbot.dto.DocumentStatusResponse;
import vn.edu.uet.chatbot.dto.DocumentUploadResponse;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionJob;
import vn.edu.uet.chatbot.ingest.service.DocumentIngestionRegistry;
import vn.edu.uet.chatbot.ingest.service.DocumentIngestionService;
import vn.edu.uet.chatbot.ingest.service.DocumentStorageService;
import vn.edu.uet.chatbot.store.QdrantVectorStore;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private static final String DEFAULT_USER = "default_user";
    private final DocumentIngestionService ingestionService;
    private final DocumentIngestionRegistry ingestionRegistry;
    private final DocumentStorageService documentStorageService;
    private final QdrantVectorStore vectorStore;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(name = "isPublic", defaultValue = "true") boolean isPublic) throws Exception {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (!isSupportedDocument(file)) {
            throw new IllegalArgumentException("Chỉ hỗ trợ file định dạng PDF, DOCX hoặc DOC");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }

        String documentId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        DocumentStorageService.StoredDocument storedDocument;
        try {
            storedDocument = documentStorageService.storeSourceFile(documentId, file);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded file", ex);
        }

        DocumentIngestionJob job;
        try {
            job = ingestionRegistry.createPending(
                    documentId,
                    title,
                    originalFilename,
                    storedDocument.path(),
                    storedDocument.sizeBytes(),
                    DEFAULT_USER,
                    true);
        } catch (RuntimeException ex) {
            try {
                documentStorageService.deleteDocument(documentId);
            } catch (IOException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            throw ex;
        }

        ingestionService.ingestDocumentAsync(documentId, title, originalFilename, storedDocument.path());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(DocumentUploadResponse.from(job, "Upload accepted"));
    }

    @GetMapping
    public ResponseEntity<java.util.List<DocumentStatusResponse>> list() {
        return ResponseEntity.ok(
                ingestionRegistry.findAll().stream()
                        .map(job -> DocumentStatusResponse.from(job,
                                documentStorageService.exists(job.storedFilePath())))
                        .toList());
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentStatusResponse> get(@PathVariable String documentId) {
        return ingestionRegistry.findById(documentId)
                .map(job -> DocumentStatusResponse.from(job, documentStorageService.exists(job.storedFilePath())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{documentId}/reindex")
    public ResponseEntity<DocumentReindexResponse> reindex(@PathVariable String documentId) {
        DocumentIngestionJob job = ingestionRegistry.findById(documentId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId));

        if (job.storedFilePath() == null || job.storedFilePath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Stored source file path is missing for document: " + documentId);
        }
        if (!documentStorageService.exists(job.storedFilePath())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Stored source file not found for document: " + documentId);
        }

        ingestionService.reindexDocument(documentId);
        return ResponseEntity.accepted().body(new DocumentReindexResponse(
                documentId,
                job.title(),
                "Reindex accepted",
                true,
                DocumentStatusResponse.from(job, true)));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<DocumentDeleteResponse> delete(@PathVariable String documentId) {
        DocumentIngestionJob job = ingestionRegistry.findById(documentId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId));

        vectorStore.deleteByDocumentId(documentId);

        boolean deletedFromStorage;
        try {
            deletedFromStorage = documentStorageService.deleteDocument(documentId);
        } catch (IOException ex) {
            log.warn("Không thể xóa file vật lý cho documentId={}, vẫn tiếp tục xóa khỏi registry. Lỗi: {}",
                    documentId, ex.getMessage());
            deletedFromStorage = false;
        }

        boolean deletedFromRegistry = ingestionRegistry.delete(documentId).isPresent();
        return ResponseEntity.ok(DocumentDeleteResponse.success(job, deletedFromRegistry, true, deletedFromStorage));
    }

    private boolean isSupportedDocument(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null)
            return false;
        String lower = originalFilename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".docx") || lower.endsWith(".doc");
    }
}
