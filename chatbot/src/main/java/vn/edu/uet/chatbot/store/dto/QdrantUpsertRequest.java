package vn.edu.uet.chatbot.store.dto;

import java.util.List;

public record QdrantUpsertRequest(List<QdrantPoint> points) {
}
