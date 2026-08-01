package vn.edu.uet.chatbot.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @JsonProperty("message")
        @JsonAlias({ "prompt", "question", "text" })
        @NotBlank String message) {
}
