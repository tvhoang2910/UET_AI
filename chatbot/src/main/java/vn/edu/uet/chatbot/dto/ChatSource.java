package vn.edu.uet.chatbot.dto;

public record ChatSource(
        String title,
        int chunkIndex,
        Integer pageNumber,
        double score,
        String textSnippet) {
}
