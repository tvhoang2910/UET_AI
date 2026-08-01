package vn.edu.uet.chatbot.dto;

import java.util.List;

public record ChatRetrievalResponse(
        List<ChatSource> sources) {
}
