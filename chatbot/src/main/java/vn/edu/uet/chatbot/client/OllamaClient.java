package vn.edu.uet.chatbot.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.edu.uet.chatbot.config.OllamaProperties;
import vn.edu.uet.chatbot.dto.ollama.Message;
import vn.edu.uet.chatbot.dto.ollama.OllamaChatRequest;
import vn.edu.uet.chatbot.dto.ollama.OllamaChatResponse;
import vn.edu.uet.chatbot.exception.LLMException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
public class OllamaClient implements LLMClient {

    private final RestClient restClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaClient(OllamaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(3000))
                .build();

        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(300000)); // 5 phút

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public String chat(String prompt) {
        return chat(List.of(new Message("user", prompt)));
    }

    @Override
    public String chat(List<Message> messages) {
        OllamaChatRequest request = new OllamaChatRequest(
                properties.getChatModel(),
                messages,
                false,
                Map.of(
                        "temperature", 0.2,
                        "num_ctx", 8192),
                false); // think = false ở cấp 1, không phải trong options

        OllamaChatResponse response = restClient.post()
                .uri("/api/chat")
                .body(request)
                .retrieve()
                .body(OllamaChatResponse.class);

        if (response == null || response.message() == null) {
            throw new LLMException("No response from Ollama");
        }
        return response.message().content();
    }

    /**
     * Stream response from Ollama /api/chat (NDJSON line-by-line).
     */
    public void chatStream(List<Message> messages, Consumer<String> onToken) {
        try {
            var reqObj = new OllamaChatRequest(
                    properties.getChatModel(),
                    messages,
                    true,
                    Map.of(
                            "temperature", 0.2,
                            "num_ctx", 8192),
                    // Đồng bộ với chat() không-stream: KHÔNG giới hạn num_predict.
                    // Trước đây đặt num_predict=256 khiến câu trả lời streaming bị
                    // cắt cụt giữa chừng dù prompt yêu cầu tối đa 250 "từ" (tiếng Việt
                    // có dấu thường tách ra nhiều token hơn số từ thực).
                    false);

            String json = objectMapper.writeValueAsString(reqObj);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofMinutes(5))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream());

            try (BufferedReader r = new BufferedReader(new InputStreamReader(response.body()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank())
                        continue;
                    OllamaChatResponse chunk = objectMapper.readValue(line,
                            OllamaChatResponse.class);

                    if (chunk != null && chunk.message() != null
                            && chunk.message().content() != null
                            && !chunk.message().content().isEmpty()) {
                        onToken.accept(chunk.message().content());
                    }

                    if (chunk != null && chunk.isDone()) {
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            throw new LLMException("Stream failed: " + ex.getMessage(), ex);
        }
    }
}
