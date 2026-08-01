package vn.edu.uet.chatbot.dto.ollama;

import java.util.List;

public record OllamaEmbedResponse(
        List<List<Double>> embeddings) {
}
