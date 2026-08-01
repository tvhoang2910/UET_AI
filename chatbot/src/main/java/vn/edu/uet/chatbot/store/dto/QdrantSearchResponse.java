package vn.edu.uet.chatbot.store.dto;

import java.util.List;
import java.util.Map;

public record QdrantSearchResponse(
        List<QdrantScoredPoint> result) {
    public record QdrantScoredPoint(
            double score,
            Map<String, Object> payload) {
    }
}
