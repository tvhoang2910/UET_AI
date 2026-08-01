package vn.edu.uet.chatbot.dto;

import java.time.Instant;

public record SystemHealthResponse(
        String status,
        Instant checkedAt,
        SystemComponentHealth ollama,
        SystemComponentHealth qdrant,
        SystemComponentHealth chatModel,
        SystemComponentHealth embeddingModel) {
}
