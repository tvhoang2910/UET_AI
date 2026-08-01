package vn.edu.uet.chatbot.dto.auth;

public record AuthResponse(
        String message,
        String token,
        String tokenType,
        String username,
        String role) {
}
