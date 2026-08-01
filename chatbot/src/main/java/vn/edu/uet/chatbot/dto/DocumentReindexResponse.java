package vn.edu.uet.chatbot.dto;

public record DocumentReindexResponse(
        String documentId,
        String title,
        String message,
        boolean accepted,
        DocumentStatusResponse status) {
}
