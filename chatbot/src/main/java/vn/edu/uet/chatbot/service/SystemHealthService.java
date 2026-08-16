package vn.edu.uet.chatbot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import vn.edu.uet.chatbot.config.QdrantProperties;
import vn.edu.uet.chatbot.dto.SystemComponentHealth;
import vn.edu.uet.chatbot.dto.SystemHealthResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemHealthService {

    private final QdrantProperties qdrantProperties;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model:}")
    private String chatModelName;

    @Value("${spring.ai.ollama.embedding.model:}")
    private String embeddingModelName;

    public SystemHealthResponse health() {
        ComponentCheck ollama = checkOllama();
        ComponentCheck qdrant = checkQdrant();
        ComponentCheck chatModel = checkOllamaModel(chatModelName, "chat");
        ComponentCheck embeddingModel = checkOllamaModel(embeddingModelName, "embedding");

        String overall = ollama.up && qdrant.up && chatModel.up && embeddingModel.up ? "UP" : "DOWN";

        return new SystemHealthResponse(
                overall,
                Instant.now(),
                toComponentHealth(ollama),
                toComponentHealth(qdrant),
                new SystemComponentHealth(chatModel.up ? "READY" : "NOT_READY", chatModel.details),
                new SystemComponentHealth(embeddingModel.up ? "READY" : "NOT_READY", embeddingModel.details));
    }

    private ComponentCheck checkOllama() {
        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = timeoutFactory();
            RestClient client = RestClient.builder()
                    .baseUrl(ollamaBaseUrl)
                    .requestFactory(requestFactory)
                    .build();

            VersionResponse response = client.get()
                    .uri("/api/version")
                    .retrieve()
                    .body(VersionResponse.class);

            if (response == null || response.version() == null || response.version().isBlank()) {
                return ComponentCheck.down("Empty version response");
            }

            return ComponentCheck.up(Map.of("version", response.version()));
        } catch (Exception ex) {
            return ComponentCheck.down(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private ComponentCheck checkQdrant() {
        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = timeoutFactory();
            RestClient client = RestClient.builder()
                    .baseUrl(qdrantProperties.getUrl())
                    .requestFactory(requestFactory)
                    .build();

            client.get()
                    .uri("/collections/{collection}", qdrantProperties.getCollection())
                    .retrieve()
                    .toBodilessEntity();

            return ComponentCheck.up(Map.of("collection", qdrantProperties.getCollection()));
        } catch (Exception ex) {
            return ComponentCheck.down(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private ComponentCheck checkOllamaModel(String model, String kind) {
        if (!isConfigured(model)) {
            return ComponentCheck.down("Model is not configured");
        }

        try {
            org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory = timeoutFactory();
            RestClient client = RestClient.builder()
                    .baseUrl(ollamaBaseUrl)
                    .requestFactory(requestFactory)
                    .build();

            Map<String, Object> body = Map.of("model", model);
            client.post()
                    .uri("/api/show")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            return ComponentCheck.up(Map.of(
                    "kind", kind,
                    "model", safeValue(model),
                    "ready", true));
        } catch (Exception ex) {
            return ComponentCheck.down(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private SystemComponentHealth toComponentHealth(ComponentCheck check) {
        return new SystemComponentHealth(check.up ? "UP" : "DOWN", check.details);
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    private String safeValue(String value) {
        return value == null ? "" : value;
    }

    private org.springframework.http.client.SimpleClientHttpRequestFactory timeoutFactory() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
        return factory;
    }

    private record ComponentCheck(boolean up, Map<String, Object> details) {
        static ComponentCheck up(Map<String, Object> details) {
            return new ComponentCheck(true, details);
        }

        static ComponentCheck down(String reason) {
            return new ComponentCheck(false, Map.of("reason", reason));
        }
    }

    private record VersionResponse(String version) {
    }
}
