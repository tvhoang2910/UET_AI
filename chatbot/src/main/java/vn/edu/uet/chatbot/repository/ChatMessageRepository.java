package vn.edu.uet.chatbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.edu.uet.chatbot.entity.ChatMessageEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findTop10BySessionIdOrderByCreatedAtDesc(UUID sessionId);

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<ChatMessageEntity> findByIdAndSessionId(UUID id, UUID sessionId);

    @Modifying
    @Query("DELETE FROM ChatMessageEntity m WHERE m.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);
}
