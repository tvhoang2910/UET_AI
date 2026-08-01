// package vn.edu.uet.chatbot.integration;

// import org.junit.jupiter.api.AfterEach;
// import org.junit.jupiter.api.Assumptions;
// import org.junit.jupiter.api.BeforeAll;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.TestInstance;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.context.SpringBootTest;
// import org.springframework.boot.test.web.client.TestRestTemplate;
// import org.springframework.core.io.ByteArrayResource;
// import org.springframework.core.io.ClassPathResource;
// import org.springframework.http.HttpEntity;
// import org.springframework.http.HttpHeaders;
// import org.springframework.http.HttpMethod;
// import org.springframework.http.HttpStatus;
// import org.springframework.http.MediaType;
// import org.springframework.http.ResponseEntity;
// import org.springframework.util.LinkedMultiValueMap;
// import org.springframework.util.MultiValueMap;
// import vn.edu.uet.chatbot.dto.ChatRequest;
// import vn.edu.uet.chatbot.dto.ChatRetrievalResponse;
// import vn.edu.uet.chatbot.dto.ChatResponse;
// import vn.edu.uet.chatbot.dto.DocumentDeleteResponse;
// import vn.edu.uet.chatbot.dto.DocumentStatusResponse;
// import vn.edu.uet.chatbot.dto.DocumentUploadResponse;
// import vn.edu.uet.chatbot.dto.auth.AuthResponse;
// import vn.edu.uet.chatbot.dto.auth.LoginRequest;
// import vn.edu.uet.chatbot.dto.auth.RegisterRequest;
// import vn.edu.uet.chatbot.ingest.model.DocumentIngestionStatus;
// import vn.edu.uet.chatbot.service.SystemHealthService;
// import vn.edu.uet.chatbot.dto.SystemHealthResponse;

// import java.io.IOException;
// import java.time.Duration;
// import java.time.Instant;
// import java.util.UUID;

// import static org.assertj.core.api.Assertions.assertThat;

// @SpringBootTest(
// webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
// properties = "documents.storage-dir=target/live-test-documents")
// @TestInstance(TestInstance.Lifecycle.PER_CLASS)
// class LiveRagIntegrationTest {

// private static final String SAMPLE_PDF =
// "Demo_Vietnamese_with_Ease_1(Học_tiếng_Việt_dễ_dàng).pdf";
// private static final String RELATED_QUESTION = "Mỗi bài học được chia thành
// các phần như thế nào?";
// private static final String UNRELATED_QUESTION = "Làm thế nào để sửa máy giặt
// bị hỏng?";
// private static final String NO_INFO_ANSWER = "Không có thông tin trong tài
// liệu được cung cấp.";
// private static final String ADMIN_PASSWORD = "Password123!";

// @Autowired
// private TestRestTemplate restTemplate;

// @Autowired
// private SystemHealthService systemHealthService;

// private String uploadedDocumentId;
// private String authToken;
// private String adminUsername;

// @BeforeAll
// void ensureLiveServicesAreAvailable() {
// SystemHealthResponse health = systemHealthService.health();
// Assumptions.assumeTrue(
// "UP".equals(health.status()),
// () -> "Skipping live integration tests because system health is " +
// health.status() + " " + health);
// authenticateAdmin();
// }

// @AfterEach
// void cleanup() {
// if (uploadedDocumentId == null) {
// return;
// }

// try {
// restTemplate.exchange(
// "/api/documents/{documentId}",
// HttpMethod.DELETE,
// authorizedEntity(),
// Void.class,
// uploadedDocumentId);
// } catch (Exception ignored) {
// // Best-effort cleanup only.
// } finally {
// uploadedDocumentId = null;
// }
// }

// @Test
// void should_upload_retrieve_chat_and_delete_document_live() throws Exception
// {
// String title = "Live Integration " + UUID.randomUUID();
// DocumentUploadResponse uploadResponse = uploadSampleDocument(title);
// uploadedDocumentId = uploadResponse.documentId();

// DocumentStatusResponse done = waitForStatus(uploadedDocumentId,
// DocumentIngestionStatus.DONE, Duration.ofSeconds(90));
// assertThat(done.status()).isEqualTo(DocumentIngestionStatus.DONE);
// assertThat(done.chunkCount()).isGreaterThan(0);
// assertThat(done.sourceFileExists()).isTrue();
// assertThat(done.reindexable()).isTrue();

// ChatRetrievalResponse retrievalResponse = restTemplate.exchange(
// "/api/chat/retrieve",
// HttpMethod.POST,
// authorizedJsonEntity(new ChatRequest(RELATED_QUESTION)),
// ChatRetrievalResponse.class)
// .getBody();
// assertThat(retrievalResponse).isNotNull();
// assertThat(retrievalResponse.sources()).isNotEmpty();
// assertThat(retrievalResponse.sources().getFirst().title()).isEqualTo(title);
// assertThat(retrievalResponse.sources().getFirst().chunkIndex()).isGreaterThanOrEqualTo(0);
// assertThat(retrievalResponse.sources().getFirst().pageNumber()).isNotNull();
// assertThat(retrievalResponse.sources().getFirst().textSnippet()).isNotBlank();

// ChatResponse chatResponse = restTemplate.exchange(
// "/api/chat",
// HttpMethod.POST,
// authorizedJsonEntity(new ChatRequest(RELATED_QUESTION)),
// ChatResponse.class)
// .getBody();
// assertThat(chatResponse).isNotNull();
// assertThat(chatResponse.answer()).isNotBlank();
// assertThat(chatResponse.answer()).isNotEqualTo(NO_INFO_ANSWER);
// assertThat(chatResponse.sources()).isNotEmpty();
// assertThat(chatResponse.sources().getFirst().title()).isEqualTo(title);

// ResponseEntity<DocumentDeleteResponse> deleteResponse =
// restTemplate.exchange(
// "/api/documents/{documentId}",
// HttpMethod.DELETE,
// authorizedEntity(),
// DocumentDeleteResponse.class,
// uploadedDocumentId);
// assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
// assertThat(deleteResponse.getBody()).isNotNull();
// assertThat(deleteResponse.getBody().deletedFromRegistry()).isTrue();
// assertThat(deleteResponse.getBody().deletedFromVectorStore()).isTrue();
// assertThat(deleteResponse.getBody().deletedFromStorage()).isTrue();

// ResponseEntity<DocumentStatusResponse> missingResponse =
// restTemplate.exchange(
// "/api/documents/{documentId}",
// HttpMethod.GET,
// authorizedEntity(),
// DocumentStatusResponse.class,
// uploadedDocumentId);
// assertThat(missingResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
// }

// @Test
// void should_return_no_information_for_unrelated_question_live() throws
// Exception {
// String title = "Live No-Info " + UUID.randomUUID();
// DocumentUploadResponse uploadResponse = uploadSampleDocument(title);
// uploadedDocumentId = uploadResponse.documentId();

// DocumentStatusResponse done = waitForStatus(uploadedDocumentId,
// DocumentIngestionStatus.DONE, Duration.ofSeconds(90));
// assertThat(done.status()).isEqualTo(DocumentIngestionStatus.DONE);

// ChatResponse chatResponse = restTemplate.exchange(
// "/api/chat",
// HttpMethod.POST,
// authorizedJsonEntity(new ChatRequest(UNRELATED_QUESTION)),
// ChatResponse.class)
// .getBody();

// assertThat(chatResponse).isNotNull();
// assertThat(chatResponse.answer()).isEqualTo(NO_INFO_ANSWER);
// assertThat(chatResponse.sources()).isEmpty();
// }

// private DocumentUploadResponse uploadSampleDocument(String title) throws
// IOException {
// byte[] pdfBytes = new
// ClassPathResource(SAMPLE_PDF).getInputStream().readAllBytes();
// ByteArrayResource fileResource = new ByteArrayResource(pdfBytes) {
// @Override
// public String getFilename() {
// return SAMPLE_PDF;
// }
// };

// MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
// body.add("file", fileResource);
// body.add("title", title);

// HttpHeaders headers = authHeaders();
// headers.setContentType(MediaType.MULTIPART_FORM_DATA);

// ResponseEntity<DocumentUploadResponse> response = restTemplate.exchange(
// "/api/documents/upload",
// HttpMethod.POST,
// new HttpEntity<>(body, headers),
// DocumentUploadResponse.class);

// assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
// assertThat(response.getBody()).isNotNull();
// assertThat(response.getBody().documentId()).isNotBlank();
// return response.getBody();
// }

// private DocumentStatusResponse waitForStatus(String documentId,
// DocumentIngestionStatus terminalStatus, Duration timeout)
// throws InterruptedException {
// Instant deadline = Instant.now().plus(timeout);
// DocumentStatusResponse lastResponse = null;

// while (Instant.now().isBefore(deadline)) {
// ResponseEntity<DocumentStatusResponse> response = restTemplate.exchange(
// "/api/documents/{documentId}",
// HttpMethod.GET,
// authorizedEntity(),
// DocumentStatusResponse.class,
// documentId);

// if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null)
// {
// lastResponse = response.getBody();
// if (lastResponse.status() == terminalStatus) {
// return lastResponse;
// }
// if (lastResponse.status() == DocumentIngestionStatus.FAILED) {
// throw new AssertionError("Document ingestion failed: " +
// lastResponse.errorMessage());
// }
// }

// Thread.sleep(1000L);
// }

// throw new AssertionError("Timed out waiting for document " + documentId + ".
// Last response=" + lastResponse);
// }

// private void authenticateAdmin() {
// adminUsername = "admin-" + UUID.randomUUID();

// ResponseEntity<AuthResponse> registerResponse = restTemplate.exchange(
// "/api/auth/register",
// HttpMethod.POST,
// jsonEntity(new RegisterRequest(adminUsername, ADMIN_PASSWORD, "ADMIN")),
// AuthResponse.class);
// assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

// ResponseEntity<AuthResponse> loginResponse = restTemplate.exchange(
// "/api/auth/login",
// HttpMethod.POST,
// jsonEntity(new LoginRequest(adminUsername, ADMIN_PASSWORD)),
// AuthResponse.class);
// assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
// assertThat(loginResponse.getBody()).isNotNull();
// authToken = loginResponse.getBody().token();
// assertThat(authToken).isNotBlank();
// }

// private HttpHeaders authHeaders() {
// HttpHeaders headers = new HttpHeaders();
// headers.setBearerAuth(authToken);
// return headers;
// }

// private HttpEntity<?> authorizedEntity() {
// return new HttpEntity<>(authHeaders());
// }

// private HttpEntity<?> authorizedJsonEntity(Object body) {
// HttpHeaders headers = authHeaders();
// headers.setContentType(MediaType.APPLICATION_JSON);
// return new HttpEntity<>(body, headers);
// }

// private HttpEntity<?> jsonEntity(Object body) {
// HttpHeaders headers = new HttpHeaders();
// headers.setContentType(MediaType.APPLICATION_JSON);
// return new HttpEntity<>(body, headers);
// }
// }
