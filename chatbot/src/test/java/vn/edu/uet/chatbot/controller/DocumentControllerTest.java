package vn.edu.uet.chatbot.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.uet.chatbot.dto.DocumentStatusResponse;
import vn.edu.uet.chatbot.exception.ApiExceptionHandler;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionJob;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionStatus;
import vn.edu.uet.chatbot.ingest.service.DocumentIngestionRegistry;
import vn.edu.uet.chatbot.ingest.service.DocumentIngestionService;
import vn.edu.uet.chatbot.ingest.service.DocumentStorageService;
import vn.edu.uet.chatbot.security.CustomUserDetailsService;
import vn.edu.uet.chatbot.security.JwtUtil;
import vn.edu.uet.chatbot.store.QdrantVectorStore;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ApiExceptionHandler.class)
class DocumentControllerTest {

    private static final Authentication USER = auth("alice", "ROLE_USER");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentIngestionService ingestionService;

    @MockitoBean
    private DocumentIngestionRegistry ingestionRegistry;

    @MockitoBean
    private DocumentStorageService documentStorageService;

    @MockitoBean
    private QdrantVectorStore vectorStore;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void should_accept_upload_and_trigger_async_ingestion() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "fake pdf".getBytes());

        when(documentStorageService.storeSourceFile(anyString(), any()))
                .thenReturn(new DocumentStorageService.StoredDocument("/tmp/documents/doc-1/source.pdf", 8L));
        when(ingestionRegistry.createPending(anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(invocation -> job(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        DocumentIngestionStatus.PENDING,
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        Instant.now(),
                        Instant.now(),
                        null,
                        null,
                        0));

        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "Demo Title")
                .principal(USER))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.title").value("Demo Title"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.message").value("Upload accepted"));

        verify(ingestionService).ingestPdfAsync(any(File.class), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void should_return_400_when_upload_is_empty() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                new byte[0]);

        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "Demo Title")
                .principal(USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("File must not be empty"));
    }

    @Test
    void should_return_400_when_file_is_not_pdf() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "plain text".getBytes());

        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "Demo Title")
                .principal(USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("File must be a PDF"));
    }

    @Test
    void should_return_400_when_title_is_blank() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "fake pdf".getBytes());

        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "")
                .principal(USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("Title must not be blank"));
    }

    @Test
    void should_return_400_when_title_contains_only_whitespace() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "fake pdf".getBytes());

        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "   ")
                .principal(USER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("Title must not be blank"));
    }

    @Test
    void should_return_richer_document_details() throws Exception {
        String documentId = "doc-123";
        when(ingestionRegistry.findById(documentId))
                .thenReturn(java.util.Optional.of(job(
                        documentId,
                        "Demo Title",
                        "sample.pdf",
                        "/tmp/documents/doc-123/source.pdf",
                        1024L,
                        DocumentIngestionStatus.DONE,
                        "alice",
                        false,
                        Instant.now(),
                        Instant.now(),
                        Instant.now(),
                        null,
                        3)));
        when(documentStorageService.exists("/tmp/documents/doc-123/source.pdf")).thenReturn(true);

        mockMvc.perform(get("/api/documents/{documentId}", documentId).principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.storedFilePath").value("/tmp/documents/doc-123/source.pdf"))
                .andExpect(jsonPath("$.fileSizeBytes").value(1024))
                .andExpect(jsonPath("$.sourceFileExists").value(true))
                .andExpect(jsonPath("$.reindexable").value(true));
    }

    @Test
    void should_list_document_metadata() throws Exception {
        when(ingestionRegistry.findAllForContext("alice", false)).thenReturn(List.of(
                job(
                        "doc-1",
                        "Title One",
                        "one.pdf",
                        "/tmp/documents/doc-1/source.pdf",
                        100L,
                        DocumentIngestionStatus.DONE,
                        "alice",
                        false,
                        Instant.now(),
                        Instant.now(),
                        Instant.now(),
                        null,
                        2),
                job(
                        "doc-2",
                        "Title Two",
                        "two.pdf",
                        "/tmp/documents/doc-2/source.pdf",
                        200L,
                        DocumentIngestionStatus.PROCESSING,
                        "bob",
                        true,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null,
                        0)));
        when(documentStorageService.exists("/tmp/documents/doc-1/source.pdf")).thenReturn(true);
        when(documentStorageService.exists("/tmp/documents/doc-2/source.pdf")).thenReturn(false);

        mockMvc.perform(get("/api/documents").principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$[0].sourceFileExists").value(true))
                .andExpect(jsonPath("$[0].reindexable").value(true))
                .andExpect(jsonPath("$[1].documentId").value("doc-2"))
                .andExpect(jsonPath("$[1].sourceFileExists").value(false))
                .andExpect(jsonPath("$[1].reindexable").value(false));
    }

    @Test
    void should_delete_document_from_registry_and_vector_store() throws Exception {
        String documentId = "doc-123";
        when(ingestionRegistry.findById(documentId))
                .thenReturn(java.util.Optional.of(job(
                        documentId,
                        "Demo Title",
                        "sample.pdf",
                        "/tmp/documents/doc-123/source.pdf",
                        1024L,
                        DocumentIngestionStatus.DONE,
                        "alice",
                        false,
                        Instant.now(),
                        Instant.now(),
                        Instant.now(),
                        null,
                        3)));
        when(ingestionRegistry.delete(documentId))
                .thenReturn(java.util.Optional.of(job(
                        documentId,
                        "Demo Title",
                        "sample.pdf",
                        "/tmp/documents/doc-123/source.pdf",
                        1024L,
                        DocumentIngestionStatus.DONE,
                        "alice",
                        false,
                        Instant.now(),
                        Instant.now(),
                        Instant.now(),
                        null,
                        3)));
        when(documentStorageService.deleteDocument(documentId)).thenReturn(true);

        mockMvc.perform(delete("/api/documents/{documentId}", documentId).principal(USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.deletedFromRegistry").value(true))
                .andExpect(jsonPath("$.deletedFromVectorStore").value(true))
                .andExpect(jsonPath("$.deletedFromStorage").value(true));

        verify(vectorStore).deleteByDocumentId(documentId);
    }

    @Test
    void should_accept_reindex_request_for_existing_document() throws Exception {
        String documentId = "doc-123";
        when(ingestionRegistry.findById(documentId))
                .thenReturn(java.util.Optional.of(job(
                        documentId,
                        "Demo Title",
                        "sample.pdf",
                        "/tmp/documents/doc-123/source.pdf",
                        1024L,
                        DocumentIngestionStatus.DONE,
                        "alice",
                        false,
                        Instant.now(),
                        Instant.now(),
                        Instant.now(),
                        null,
                        3)));
        when(documentStorageService.exists("/tmp/documents/doc-123/source.pdf")).thenReturn(true);

        mockMvc.perform(post("/api/documents/{documentId}/reindex", documentId).principal(USER))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentId").value(documentId))
                .andExpect(jsonPath("$.accepted").value(true));

        verify(ingestionService).reindexDocument(documentId);
    }

    @Test
    void should_generate_different_document_ids_for_uploads_with_same_title() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "fake pdf".getBytes());

        when(documentStorageService.storeSourceFile(anyString(), any()))
                .thenAnswer(invocation -> new DocumentStorageService.StoredDocument(
                        "/tmp/documents/" + invocation.getArgument(0) + "/source.pdf",
                        8L));
        when(ingestionRegistry.createPending(anyString(), anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(), anyString(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenAnswer(invocation -> job(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        DocumentIngestionStatus.PENDING,
                        invocation.getArgument(5),
                        invocation.getArgument(6),
                        Instant.now(),
                        Instant.now(),
                        null,
                        null,
                        0));

        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "Same Title")
                .principal(USER))
                .andExpect(status().isAccepted());

        mockMvc.perform(multipart("/api/documents/upload")
                .file(file)
                .param("title", "Same Title")
                .principal(USER))
                .andExpect(status().isAccepted());

        var documentIdCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(documentStorageService, times(2)).storeSourceFile(documentIdCaptor.capture(), any());

        List<String> documentIds = new ArrayList<>(documentIdCaptor.getAllValues());
        assertThat(documentIds).hasSize(2);
        assertThat(documentIds.get(0)).isNotEqualTo(documentIds.get(1));
    }

    private static Authentication auth(String username, String... roles) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        return new UsernamePasswordAuthenticationToken(username, "n/a", authorities);
    }

    private static DocumentIngestionJob job(
            String documentId,
            String title,
            String originalFilename,
            String storedFilePath,
            long fileSizeBytes,
            DocumentIngestionStatus status,
            String owner,
            boolean isPublic,
            Instant createdAt,
            Instant updatedAt,
            Instant finishedAt,
            String errorMessage,
            int chunkCount) {
        return new DocumentIngestionJob(
                documentId,
                title,
                originalFilename,
                storedFilePath,
                fileSizeBytes,
                status,
                createdAt,
                updatedAt,
                finishedAt,
                errorMessage,
                chunkCount,
                owner,
                isPublic);
    }
}
