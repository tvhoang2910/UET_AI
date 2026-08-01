package vn.edu.uet.chatbot.store.dto;

import vn.edu.uet.chatbot.ingest.model.DocumentChunk;

public record ScoredDocumentChunk(
        DocumentChunk chunk,
        double score) {
}
