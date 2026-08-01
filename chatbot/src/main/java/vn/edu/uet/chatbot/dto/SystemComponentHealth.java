package vn.edu.uet.chatbot.dto;

import java.util.Map;

public record SystemComponentHealth(
        String status,
        Map<String, Object> details) {
}
