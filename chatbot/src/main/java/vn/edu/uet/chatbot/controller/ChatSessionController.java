package vn.edu.uet.chatbot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
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

    private final ChatService chatService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    @PostMapping
    public ResponseEntity<ChatSessionEntity> createSession(
            @RequestParam(name = "title", defaultValue = "Hội thoại mới") String title,
            Authentication authentication) {
        ChatSessionEntity session = new ChatSessionEntity();
        session.setUsername(authentication.getName());
        session.setTitle(title);
        return ResponseEntity.ok(sessionRepository.save(session));
    }

    @GetMapping
    public ResponseEntity<List<ChatSessionEntity>> getSessions(Authentication authentication) {
        return ResponseEntity.ok(sessionRepository.findByUsernameOrderByCreatedAtDesc(authentication.getName()));
    }

    /**
     * Trả về toàn bộ lịch sử tin nhắn (đã lưu trong MariaDB) của một session.
     * Trước đây frontend chỉ dựa vào localStorage để hiển thị lại hội thoại,
     * nghĩa là đổi máy/trình duyệt hoặc xóa cache là mất sạch lịch sử hiển thị
     * dù dữ liệu vẫn còn trong DB. Endpoint này khắc phục vấn đề đó.
     */
    @GetMapping("/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(
            @PathVariable UUID sessionId,
            Authentication authentication) {
        ChatSessionEntity session = requireOwnedSession(sessionId, authentication);
        List<ChatMessageResponse> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
        return ResponseEntity.ok(messages);
    }

    @PostMapping("/{sessionId}")
    public ResponseEntity<ChatResponse> chat(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(chatService.chat(sessionId, request.message(), authentication.getName()));
    }

    @PostMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @PathVariable UUID sessionId,
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        return chatService.chatStream(sessionId, request.message(), authentication.getName());
    }

    /**
     * Gửi đánh giá 👍/👎 cho một câu trả lời của trợ lý — dữ liệu feedback loop
     * dùng để sau này tinh chỉnh threshold, prompt, hoặc reranker.
     */
    @PatchMapping("/{sessionId}/messages/{messageId}/feedback")
    public ResponseEntity<ChatMessageResponse> submitFeedback(
            @PathVariable UUID sessionId,
            @PathVariable UUID messageId,
            @Valid @RequestBody FeedbackRequest request,
            Authentication authentication) {
        if (request.value() != 1 && request.value() != -1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "value chỉ được nhận giá trị 1 (👍) hoặc -1 (👎)");
        }

        ChatSessionEntity session = requireOwnedSession(sessionId, authentication);

        ChatMessageEntity message = messageRepository.findByIdAndSessionId(messageId, session.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));

        if (!"assistant".equals(message.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể đánh giá phản hồi của trợ lý");
        }

        message.setFeedback(request.value());
        ChatMessageEntity saved = messageRepository.save(message);
        return ResponseEntity.ok(ChatMessageResponse.from(saved));
    }

    @DeleteMapping("/{sessionId}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId, Authentication authentication) {
        ChatSessionEntity session = requireOwnedSession(sessionId, authentication);

        // Xóa toàn bộ message của session trước, tránh dữ liệu mồ côi
        messageRepository.deleteBySessionId(session.getId());
        sessionRepository.delete(session);

        return ResponseEntity.ok().build();
    }

    private ChatSessionEntity requireOwnedSession(UUID sessionId, Authentication authentication) {
        ChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên chat"));

        if (!session.getUsername().equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập phiên chat này");
        }
        return session;
    }
}
