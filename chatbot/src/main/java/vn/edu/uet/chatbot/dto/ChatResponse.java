package vn.edu.uet.chatbot.dto;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID id,
        String answer,
        List<ChatSource> sources) {
}
