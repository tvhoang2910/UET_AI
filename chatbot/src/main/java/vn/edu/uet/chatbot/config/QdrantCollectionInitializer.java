package vn.edu.uet.chatbot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.edu.uet.chatbot.embed.EmbeddingClient;
import vn.edu.uet.chatbot.store.dto.QdrantCreateCollectionRequest;
import vn.edu.uet.chatbot.store.dto.QdrantVectorParams;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class QdrantCollectionInitializer implements CommandLineRunner {

    private final QdrantProperties qdrantProperties;
    private final EmbeddingClient embeddingClient;

    @Override
    public void run(String... args) {
        RestClient client = RestClient.builder()
                .baseUrl(qdrantProperties.getUrl())
                .build();

        boolean exists;
        try {
            exists = client.get()
                    .uri("/collections/{collection}", qdrantProperties.getCollection())
                    .retrieve()
                    .toBodilessEntity()
                    .getStatusCode()
                    .is2xxSuccessful();
        } catch (Exception ex) {
            exists = false;
        }

        if (exists) {
            return;
        }

        int dimension = 0;
        for (int i = 0; i < 5; i++) {
            try {
                dimension = embeddingClient.embed("dimension probe").size();
                break;
            } catch (Exception ex) {
                log.warn("Ollama chưa sẵn sàng, retry {}/5: {}", i + 1, ex.getMessage());
                try {
                    Thread.sleep(2000 * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
        if (dimension == 0)
            return; // vẫn cho app lên

        QdrantCreateCollectionRequest request = new QdrantCreateCollectionRequest(
                new QdrantVectorParams(dimension, "Cosine"));

        try {
            client.put()
                    .uri("/collections/{collection}", qdrantProperties.getCollection())
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Qdrant collection created: {}", qdrantProperties.getCollection());
        } catch (Exception ex) {
            // QUAN TRỌNG: nếu bước exists-check ở trên bị lỗi tạm thời (mạng chập
            // chờn) trong khi collection thực ra ĐÃ tồn tại, lệnh PUT tạo collection
            // phía trên có thể thất bại. Trước đây lỗi này không được bắt, khiến
            // CommandLineRunner ném exception và làm SẬP luôn quá trình khởi động
            // Spring Boot. Giờ chỉ log cảnh báo và bỏ qua, không để một lỗi tạm thời
            // khi khởi tạo Qdrant làm chết cả ứng dụng.
            log.warn("Không thể tạo Qdrant collection '{}' (có thể đã tồn tại hoặc lỗi tạm thời), " +
                    "bỏ qua và tiếp tục khởi động ứng dụng: {}",
                    qdrantProperties.getCollection(), ex.getMessage());
            return;
        }

        // Create payload indexes (best-effort; never fail app startup)
        try {
            // owner -> keyword index
            client.put()
                    .uri("/collections/{collection}/index", qdrantProperties.getCollection())
                    .body(Map.of(
                            "field_name", "owner",
                            "field_schema", Map.of("type", "keyword")))
                    .retrieve()
                    .toBodilessEntity();

            // isPublic -> keyword index (works with boolean payload stored as native bool)
            client.put()
                    .uri("/collections/{collection}/index", qdrantProperties.getCollection())
                    .body(Map.of(
                            "field_name", "isPublic",
                            "field_schema", Map.of("type", "keyword")))
                    .retrieve()
                    .toBodilessEntity();

            // documentId -> keyword index (speeds up deleteByDocumentId)
            client.put()
                    .uri("/collections/{collection}/index", qdrantProperties.getCollection())
                    .body(Map.of(
                            "field_name", "documentId",
                            "field_schema", Map.of("type", "keyword")))
                    .retrieve()
                    .toBodilessEntity();

            // category -> keyword index
            client.put()
                    .uri("/collections/{collection}/index", qdrantProperties.getCollection())
                    .body(Map.of(
                            "field_name", "category",
                            "field_schema", Map.of("type", "keyword")))
                    .retrieve()
                    .toBodilessEntity();

            log.info("Qdrant payload indexes created for: owner, isPublic, documentId, category (collection={})",
                    qdrantProperties.getCollection());
        } catch (Exception ex) {
            log.warn("Skip Qdrant payload index creation due to compatibility/version error: {}",
                    ex.getMessage());
        }
    }
}
