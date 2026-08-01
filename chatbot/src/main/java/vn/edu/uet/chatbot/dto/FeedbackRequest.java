package vn.edu.uet.chatbot.dto;

import jakarta.validation.constraints.NotNull;

public record FeedbackRequest(@NotNull Integer value) {
}
