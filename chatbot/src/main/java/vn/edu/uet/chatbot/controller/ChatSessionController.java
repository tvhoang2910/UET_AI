package vn.edu.uet.chatbot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.edu.uet.chatbot.dto.ChatMessageResponse;
import vn.edu.uet.chatbot.dto.ChatRequest;
import vn.edu.uet.chatbot.dto.ChatResponse;
import vn.edu.uet.chatbot.dto.FeedbackRequest;
import vn.edu.uet.chatbot.entity.ChatMessageEntity;
import vn.edu.uet.chatbot.entity.ChatSessionEntity;
import vn.edu.uet.chatbot.repository.ChatMessageRepository;
import vn.edu.uet.chatbot.repository.ChatSessionRepository;
import vn.edu.uet.chatbot.service.ChatService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat/sessions")
@RequiredArgsConstructor
public class ChatSessionController {

    private static final String DEFAULT_USER = "default_user";
    private final ChatService chatService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    @PostMapping
    public ResponseEntity<ChatSessionEntity> createSession(
            @RequestParam(name = "title", defaultValue = "Hội thoại mới") String title) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setUsername(DEFAULT_USER);
        session.setTitle(title);
        return ResponseEntity.ok(sessionRepository.save(session));
    }

    @GetMapping
    public ResponseEntity<List<ChatSessionEntity>> getSessions() {
        return ResponseEntity.ok(sessionRepository.findByUsernameOrderByCreatedAtDesc(DEFAULT_USER));
    }

    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable UUID sessionId) {
        ChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên chat"));

        List<ChatMessageResponse> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{sessionId}")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.chat(sessionId, request.message(), DEFAULT_USER));
    }

    @PostMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatRequest request) {
        return chatService.chatStream(sessionId, request.message(), DEFAULT_USER);
    }

    @PatchMapping("/{sessionId}/messages/{messageId}/feedback")
    public ResponseEntity<ChatMessageResponse> submitFeedback(
            @PathVariable UUID sessionId,
            @PathVariable UUID messageId,
            @Valid @RequestBody FeedbackRequest request) {
        if (request.value() != 1 && request.value() != -1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "value chỉ được nhận 1 hoặc -1");
        }

        ChatMessageEntity message = messageRepository.findByIdAndSessionId(messageId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));

        message.setFeedback(request.value());
        return ResponseEntity.ok(ChatMessageResponse.from(messageRepository.save(message)));
    }

    @DeleteMapping("/{sessionId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
        return ResponseEntity.ok().build();
    }
}
