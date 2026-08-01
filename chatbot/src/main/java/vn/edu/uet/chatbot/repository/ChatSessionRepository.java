package vn.edu.uet.chatbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uet.chatbot.entity.ChatSessionEntity;

import java.util.List;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {
    List<ChatSessionEntity> findByUsernameOrderByCreatedAtDesc(String username);
}
