package vn.edu.uet.chatbot.ingest.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(DocumentIngestionRegistry.class)
class DocumentIngestionRegistryTest {

    @Autowired
    private DocumentIngestionRegistry registry;

    @Test
    void should_persist_update_and_delete_document_jobs() {
        var created = registry.createPending("doc-1", "Quy che", "quy-che.pdf");

        assertThat(created.status()).isEqualTo(DocumentIngestionStatus.PENDING);
        assertThat(registry.findById("doc-1")).isPresent();

        var processing = registry.markProcessing("doc-1");
        assertThat(processing.status()).isEqualTo(DocumentIngestionStatus.PROCESSING);

        var done = registry.markDone("doc-1", 7);
        assertThat(done.status()).isEqualTo(DocumentIngestionStatus.DONE);
        assertThat(done.chunkCount()).isEqualTo(7);

        var deleted = registry.delete("doc-1");
        assertThat(deleted).isPresent();
        assertThat(registry.findById("doc-1")).isEmpty();
    }
}
