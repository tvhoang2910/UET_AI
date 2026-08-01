package vn.edu.uet.chatbot.dto.ollama;

public record OllamaEmbedRequest(
        String model,
        Object input) {
}