package vn.edu.uet.chatbot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.uet.chatbot.dto.DocumentReindexResponse;
import vn.edu.uet.chatbot.dto.DocumentStatusResponse;
import vn.edu.uet.chatbot.dto.DocumentDeleteResponse;
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

    private final DocumentIngestionService ingestionService;
    private final DocumentIngestionRegistry ingestionRegistry;
    private final DocumentStorageService documentStorageService;
    private final QdrantVectorStore vectorStore;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(name = "isPublic", defaultValue = "false") boolean isPublic,
            Authentication authentication) throws Exception {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty");
        }
        if (!isPdfUpload(file)) {
            throw new IllegalArgumentException("File must be a PDF");
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
            Authentication currentAuth = requireAuthentication(authentication);
            job = ingestionRegistry.createPending(
                    documentId,
                    title,
                    originalFilename,
                    storedDocument.path(),
                    storedDocument.sizeBytes(),
                    currentAuth.getName(),
                    isAdmin(currentAuth) || isPublic);
        } catch (RuntimeException ex) {
            try {
                documentStorageService.deleteDocument(documentId);
            } catch (IOException cleanupEx) {
                ex.addSuppressed(cleanupEx);
            }
            throw ex;
        }

        ingestionService.ingestPdfAsync(new File(storedDocument.path()), documentId, title, originalFilename, false);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(DocumentUploadResponse.from(job, "Upload accepted"));
    }

    @GetMapping
    public ResponseEntity<java.util.List<DocumentStatusResponse>> list(Authentication authentication) {
        Authentication currentAuth = requireAuthentication(authentication);
        String myUser = currentAuth.getName();
        boolean imAdmin = isAdmin(currentAuth);
        return ResponseEntity.ok(
                ingestionRegistry.findAllForContext(myUser, imAdmin).stream()
                        .map(job -> DocumentStatusResponse.from(job,
                                documentStorageService.exists(job.storedFilePath())))
                        .toList());
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<DocumentStatusResponse> get(@PathVariable String documentId, Authentication authentication) {
        Authentication currentAuth = requireAuthentication(authentication);
        return ingestionRegistry.findById(documentId)
                .map(job -> {
                    assertCanView(job, currentAuth);
                    return job;
                })
                .map(job -> DocumentStatusResponse.from(job, documentStorageService.exists(job.storedFilePath())))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{documentId}/reindex")
    public ResponseEntity<DocumentReindexResponse> reindex(@PathVariable String documentId,
            Authentication authentication) {
        Authentication currentAuth = requireAuthentication(authentication);
        DocumentIngestionJob job = ingestionRegistry.findById(documentId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId));
        assertCanModify(job, currentAuth);

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
    public ResponseEntity<DocumentDeleteResponse> delete(@PathVariable String documentId,
            Authentication authentication) {
        Authentication currentAuth = requireAuthentication(authentication);
        DocumentIngestionJob job = ingestionRegistry.findById(documentId)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + documentId));
        assertCanModify(job, currentAuth);

        vectorStore.deleteByDocumentId(documentId);

        boolean deletedFromStorage;
        try {
            deletedFromStorage = documentStorageService.deleteDocument(documentId);
        } catch (IOException ex) {
            // QUAN TRỌNG: vector đã bị xóa ở bước trên (khó hoàn tác), nên nếu ném lỗi
            // 500 tại đây và dừng luôn (như code cũ), registry sẽ vẫn giữ job này với
            // trạng thái DONE dù vector không còn -> tạo ra "tài liệu ma": vẫn hiện trong
            // danh sách, trông như sẵn sàng, nhưng truy vấn RAG sẽ không bao giờ trả về
            // kết quả nào từ nó. Thay vào đó: log cảnh báo để admin dọn file thủ công sau,
            // đặt deletedFromStorage=false, và VẪN tiếp tục xóa khỏi registry để tránh
            // trạng thái không nhất quán.
            log.warn("Không thể xóa file vật lý cho documentId={}, vẫn tiếp tục xóa khỏi registry. Lỗi: {}",
                    documentId, ex.getMessage());
            deletedFromStorage = false;
        }

        boolean deletedFromRegistry = ingestionRegistry.delete(documentId).isPresent();

        return ResponseEntity.ok(DocumentDeleteResponse.success(job, deletedFromRegistry, true, deletedFromStorage));
    }

    private boolean isPdfUpload(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();

        boolean filenameLooksLikePdf = originalFilename != null
                && originalFilename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        boolean contentTypeLooksLikePdf = MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType);

        return filenameLooksLikePdf || contentTypeLooksLikePdf;
    }

    private Authentication requireAuthentication(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        return authentication;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(role -> role.getAuthority().equals("ROLE_ADMIN"));
    }

    private void assertCanView(DocumentIngestionJob job, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        if (job.isPublic()) {
            return;
        }
        if (job.owner() != null && job.owner().equals(authentication.getName())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Bạn chỉ được thao tác với file thuộc bộ sở hữu của cá nhân !");
    }

    private void assertCanModify(DocumentIngestionJob job, Authentication authentication) {
        if (isAdmin(authentication)) {
            return;
        }
        if (job.owner() != null && job.owner().equals(authentication.getName())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Bạn chỉ được thao tác với file thuộc bộ sở hữu của cá nhân !");
    }
}
