package vn.edu.uet.chatbot.store.dto;

import java.util.List;
import java.util.Map;

public record QdrantPoint(
        String id,
        List<Double> vector,
        Map<String, Object> payload) {
}
