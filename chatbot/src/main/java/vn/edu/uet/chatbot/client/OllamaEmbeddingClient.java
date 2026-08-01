package vn.edu.uet.chatbot.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.edu.uet.chatbot.config.OllamaProperties;
import vn.edu.uet.chatbot.dto.ollama.OllamaEmbedRequest;
import vn.edu.uet.chatbot.dto.ollama.OllamaEmbedResponse;
import vn.edu.uet.chatbot.embed.EmbeddingClient;
import vn.edu.uet.chatbot.exception.LLMException;

import java.util.List;

@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final OllamaProperties properties;

    public OllamaEmbeddingClient(OllamaProperties properties) {
        this.properties = properties;

        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofMillis(3000))
                .build();

        org.springframework.http.client.JdkClientHttpRequestFactory requestFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                httpClient);
        requestFactory.setReadTimeout(java.time.Duration.ofMillis(600000));

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public List<Double> embed(String text) {
        List<List<Double>> result = embedBatch(List.of(text));
        if (result.isEmpty()) {
            throw new LLMException("No embedding response from Ollama");
        }
        return result.get(0);
    }

    @Override
    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        OllamaEmbedRequest request = new OllamaEmbedRequest(properties.getEmbeddingModel(), texts);

        OllamaEmbedResponse response = restClient.post()
                .uri("/api/embed")
                .body(request)
                .retrieve()
                .body(OllamaEmbedResponse.class);

        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new LLMException("No embedding response from Ollama");
        }
        if (response.embeddings().size() != texts.size()) {
            throw new LLMException(
                    "Ollama returned " + response.embeddings().size() + " embeddings for " + texts.size()
                            + " inputs (mismatch)");
        }

        return response.embeddings();
    }
}