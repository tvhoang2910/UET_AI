package vn.edu.uet.chatbot.ingest.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.chatbot.ingest.entity.DocumentIngestionEntity;

import java.util.List;

public interface DocumentIngestionRepository extends JpaRepository<DocumentIngestionEntity, String> {

    @Query("SELECT d FROM DocumentIngestionEntity d WHERE d.owner = :username OR d.isPublic = true OR :isAdmin = true ORDER BY d.createdAt DESC")
    List<DocumentIngestionEntity> findAllForContext(@Param("username") String username,
            @Param("isAdmin") boolean isAdmin);

    @Modifying
    @Transactional
    @Query("""
            UPDATE DocumentIngestionEntity d
            SET d.status = 'FAILED',
                d.errorMessage = 'Ứng dụng bị dừng đột ngột trong quá trình xử lý ngầm.'
            WHERE d.status IN ('PENDING', 'PROCESSING')
            """)
    int failStuckJobsOnStartup();
}
