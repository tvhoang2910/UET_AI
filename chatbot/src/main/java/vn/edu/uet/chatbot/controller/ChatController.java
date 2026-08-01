package vn.edu.uet.chatbot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import vn.edu.uet.chatbot.dto.ChatRetrievalResponse;
import vn.edu.uet.chatbot.dto.ChatRequest;
import vn.edu.uet.chatbot.dto.ChatResponse;
import vn.edu.uet.chatbot.service.ChatService;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/api/chat")
    public ChatResponse chat(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        return chatService.chat(request.message(), authentication.getName());
    }

    @PostMapping("/api/chat/retrieve")
    public ChatRetrievalResponse retrieve(@Valid @RequestBody ChatRequest request, Authentication authentication) {
        return new ChatRetrievalResponse(chatService.inspect(request.message(), authentication.getName()));
    }
}
