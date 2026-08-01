package vn.edu.uet.chatbot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import vn.edu.uet.chatbot.ingest.repository.DocumentIngestionRepository;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionRecoveryInitializer implements CommandLineRunner {

    private final DocumentIngestionRepository repository;

    @Override
    public void run(String... args) {
        try {
            int recoveredJobs = repository.failStuckJobsOnStartup();
            if (recoveredJobs > 0) {
                log.info(
                        "Hệ thống khởi động: Đã đánh dấu THẤT BẠI cho {} tác vụ tải tài liệu bị kẹt trước đó.",
                        recoveredJobs);
            }
        } catch (Exception ex) {
            log.error("Không thể khôi phục các tác vụ tài liệu bị kẹt khi khởi động: {}", ex.getMessage());
        }
    }
}
