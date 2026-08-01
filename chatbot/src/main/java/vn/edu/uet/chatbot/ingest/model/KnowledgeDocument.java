package vn.edu.uet.chatbot.ingest.model;

import java.util.Map;

public record KnowledgeDocument(
        String id,
        String title,
        String source,
        String content,
        Map<String, Object> metadata) {
}
