package vn.edu.uet.chatbot.dto.ollama;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OllamaChatRequest(
        String model,
        List<Message> messages,
        boolean stream,
        Map<String, Object> options,
        Boolean think) {

    public OllamaChatRequest(String model, List<Message> messages, boolean stream, Map<String, Object> options) {
        this(model, messages, stream, options, false);
    }
}