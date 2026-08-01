package vn.edu.uet.chatbot.dto.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(
                Message message,
                Boolean done) {

        public boolean isDone() {
                return Boolean.TRUE.equals(done);
        }
}
