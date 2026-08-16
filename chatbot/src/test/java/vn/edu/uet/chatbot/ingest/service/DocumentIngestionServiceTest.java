package vn.edu.uet.chatbot.ingest.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.client.RestClientException;
import vn.edu.uet.chatbot.chunk.ChunkSegment;
import vn.edu.uet.chatbot.chunk.TextChunker;
import vn.edu.uet.chatbot.config.RagProperties;
import vn.edu.uet.chatbot.ingest.extractor.DocumentTextExtractor;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionJob;
import vn.edu.uet.chatbot.ingest.model.DocumentPageText;
import vn.edu.uet.chatbot.store.VectorStore;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

        @Mock
        private DocumentTextExtractor pdfTextExtractor;

        @Mock
        private TextChunker textChunker;

        @Mock
        private EmbeddingModel embeddingModel;

        @Mock
        private VectorStore vectorStore;

        @Mock
        private DocumentIngestionRegistry ingestionRegistry;

        @Mock
        private DocumentStorageService documentStorageService;

        @Mock
        private RagProperties ragProperties;

        @InjectMocks
        private DocumentIngestionService ingestionService;

        @Test
        void should_ingest_pdf_and_mark_job_done() throws Exception {
                String documentId = "doc-1";
                String title = "Demo Title";
                String originalFilename = "demo.pdf";

                when(ragProperties.getEmbeddingBatchSize()).thenReturn(16);
                when(ingestionRegistry.findById(documentId))
                                .thenReturn(Optional.of(DocumentIngestionJob.pending(
                                                documentId,
                                                title,
                                                originalFilename,
                                                "/tmp/documents/doc-1/source.pdf",
                                                123L)));
                when(pdfTextExtractor.extractPages(any(File.class)))
                                .thenReturn(List.of(new DocumentPageText(1, "Nội dung trang 1")));
                when(textChunker.chunk("Nội dung trang 1", 1))
                                .thenReturn(List.of(new ChunkSegment("Nội dung trang 1", 1)));
                when(embeddingModel.embed(anyList()))
                                .thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f }));

                CompletableFuture<Void> future = ingestionService.ingestPdfAsync(
                                new File("ignored.pdf"),
                                documentId,
                                title,
                                originalFilename,
                                false);

                assertThat(future.join()).isNull();
                verify(ingestionRegistry).markProcessing(documentId);
                verify(vectorStore).upsert(anyString(), anyList(), anyList());
                verify(ingestionRegistry).markDone(documentId, 1);
        }

        @Test
        void should_mark_job_failed_when_embedding_backend_is_down() throws Exception {
                String documentId = "doc-2";
                String title = "Demo Title";
                String originalFilename = "demo.pdf";

                when(ragProperties.getEmbeddingBatchSize()).thenReturn(16);
                when(ingestionRegistry.findById(documentId))
                                .thenReturn(Optional.of(DocumentIngestionJob.pending(
                                                documentId,
                                                title,
                                                originalFilename,
                                                "/tmp/documents/doc-2/source.pdf",
                                                123L)));
                when(pdfTextExtractor.extractPages(any(File.class)))
                                .thenReturn(List.of(new DocumentPageText(1, "Nội dung trang 1")));
                when(textChunker.chunk("Nội dung trang 1", 1))
                                .thenReturn(List.of(new ChunkSegment("Nội dung trang 1", 1)));
                when(embeddingModel.embed(anyList()))
                                .thenThrow(new RestClientException("Ollama is unavailable"));

                CompletableFuture<Void> future = ingestionService.ingestPdfAsync(
                                new File("ignored.pdf"),
                                documentId,
                                title,
                                originalFilename,
                                false);

                assertThat(future).isCompletedExceptionally();
                verify(ingestionRegistry).markProcessing(documentId);
                verify(ingestionRegistry).markFailed(documentId, "Ollama is unavailable");
        }

        @Test
        void should_mark_job_failed_when_qdrant_upsert_is_down() throws Exception {
                String documentId = "doc-3";
                String title = "Demo Title";
                String originalFilename = "demo.pdf";

                when(ragProperties.getEmbeddingBatchSize()).thenReturn(16);
                when(ingestionRegistry.findById(documentId))
                                .thenReturn(Optional.of(DocumentIngestionJob.pending(
                                                documentId,
                                                title,
                                                originalFilename,
                                                "/tmp/documents/doc-3/source.pdf",
                                                123L)));
                when(pdfTextExtractor.extractPages(any(File.class)))
                                .thenReturn(List.of(new DocumentPageText(1, "Nội dung trang 1")));
                when(textChunker.chunk("Nội dung trang 1", 1))
                                .thenReturn(List.of(new ChunkSegment("Nội dung trang 1", 1)));
                when(embeddingModel.embed(anyList()))
                                .thenReturn(List.of(new float[] { 0.1f, 0.2f, 0.3f }));
                doThrow(new RestClientException("Qdrant is unavailable"))
                                .when(vectorStore).upsert(anyString(), anyList(), anyList());

                CompletableFuture<Void> future = ingestionService.ingestPdfAsync(
                                new File("ignored.pdf"),
                                documentId,
                                title,
                                originalFilename,
                                false);

                assertThat(future).isCompletedExceptionally();
                verify(ingestionRegistry).markProcessing(documentId);
                verify(ingestionRegistry).markFailed(documentId, "Qdrant is unavailable");
        }
}
