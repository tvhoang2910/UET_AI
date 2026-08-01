package vn.edu.uet.chatbot.dto;

import vn.edu.uet.chatbot.entity.ChatMessageEntity;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        String role,
        String content,
        Instant createdAt,
        Integer feedback) {

    public static ChatMessageResponse from(ChatMessageEntity entity) {
        return new ChatMessageResponse(
                entity.getId(),
                entity.getRole(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getFeedback());
    }
}
