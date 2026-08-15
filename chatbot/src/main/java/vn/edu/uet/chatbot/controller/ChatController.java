package vn.edu.uet.chatbot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.edu.uet.chatbot.dto.ChatRetrievalResponse;
import vn.edu.uet.chatbot.dto.ChatRequest;
import vn.edu.uet.chatbot.dto.ChatResponse;
import vn.edu.uet.chatbot.service.ChatService;

@RestController
@RequiredArgsConstructor
public class ChatController {

    private static final String DEFAULT_USER = "default_user";
    private final ChatService chatService;

    @PostMapping("/api/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request) {
        return chatService.chat(request.message(), DEFAULT_USER);
    }

    @PostMapping("/api/chat/retrieve")
    public ChatRetrievalResponse retrieve(@Valid @RequestBody ChatRequest request) {
        return new ChatRetrievalResponse(chatService.inspect(request.message(), DEFAULT_USER));
    }
}
