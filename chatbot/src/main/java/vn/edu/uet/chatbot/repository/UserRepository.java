package vn.edu.uet.chatbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.uet.chatbot.entity.UserEntity;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByUsername(String username);

    boolean existsByUsername(String username);
}
