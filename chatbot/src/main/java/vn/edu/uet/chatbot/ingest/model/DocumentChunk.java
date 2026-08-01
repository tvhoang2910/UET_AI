package vn.edu.uet.chatbot.ingest.model;

import java.util.Map;

public record DocumentChunk(
        String documentId,
        int chunkIndex,
        Integer pageNumber,
        String content,
        Map<String, Object> metadata) {
}
