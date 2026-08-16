package vn.edu.uet.chatbot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.uet.chatbot.client.OllamaClient;
import vn.edu.uet.chatbot.config.RagProperties;
import vn.edu.uet.chatbot.dto.ChatResponse;
import vn.edu.uet.chatbot.embed.EmbeddingClient;
import vn.edu.uet.chatbot.ingest.model.DocumentChunk;
import vn.edu.uet.chatbot.prompt.ChatPromptBuilder;
import vn.edu.uet.chatbot.store.QdrantVectorStore;
import vn.edu.uet.chatbot.store.dto.ScoredDocumentChunk;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final String USERNAME = "alice";

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private QdrantVectorStore vectorStore;

    @Mock
    private OllamaClient ollamaClient;

    @Mock
    private RagProperties ragProperties;

    @Spy
    private ChatPromptBuilder promptBuilder = new ChatPromptBuilder();

    @InjectMocks
    private ChatService chatService;

    @Test
    void should_return_no_info_without_calling_llm_when_no_chunks_are_found() {
        when(ragProperties.getTopK()).thenReturn(5);
        when(ragProperties.getScoreThreshold()).thenReturn(0.6);
        when(embeddingClient.embed("Mỗi bài học được chia thành các phần như thế nào?"))
                .thenReturn(List.of(0.1, 0.2, 0.3));

        DocumentChunk lowScoreChunk = new DocumentChunk(
                "Tiếng Việt Dễ Dàng",
                0,
                1,
                "Nội dung không đạt ngưỡng.",
                Map.of("title", "Tiếng Việt Dễ Dàng"));
        when(vectorStore.searchWithScores(anyList(), anyInt(), anyString(), anyDouble()))
                .thenReturn(List.of(new ScoredDocumentChunk(lowScoreChunk, 0.59)));

        ChatResponse response = chatService.chat("Mỗi bài học được chia thành các phần như thế nào?", USERNAME);

        assertThat(response.answer()).isEqualTo("Không có thông tin trong tài liệu được cung cấp.");
        assertThat(response.sources()).isEmpty();
        verify(ollamaClient, never()).chat(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void should_include_source_context_in_prompt_when_chunks_are_found() {
        when(ragProperties.getTopK()).thenReturn(5);
        when(ragProperties.getScoreThreshold()).thenReturn(0.6);
        when(embeddingClient.embed("Mỗi bài học được chia thành các phần như thế nào?"))
                .thenReturn(List.of(0.1, 0.2, 0.3));

        DocumentChunk chunk = new DocumentChunk(
                "Tiếng Việt Dễ Dàng",
                0,
                2,
                "Mỗi bài học được chia thành các phần như sau: Từ vựng, Hội thoại và Ngữ pháp, Bài đọc...",
                Map.of("title", "Tiếng Việt Dễ Dàng"));

        when(vectorStore.searchWithScores(anyList(), anyInt(), anyString(), anyDouble()))
                .thenReturn(List.of(new ScoredDocumentChunk(chunk, 0.91)));
        when(ollamaClient.chat(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("Mỗi bài học được chia thành 7 phần.");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        ChatResponse response = chatService.chat("Mỗi bài học được chia thành các phần như thế nào?", USERNAME);

        verify(ollamaClient).chat(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("--- [NGUỒN 1 | TÀI LIỆU: \"Tiếng Việt Dễ Dàng\" | TRANG: 2] ---");
        assertThat(promptCaptor.getValue()).contains("Mỗi bài học được chia thành các phần như sau");
        assertThat(response.answer()).isEqualTo("Mỗi bài học được chia thành 7 phần.");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().title()).isEqualTo("Tiếng Việt Dễ Dàng");
        assertThat(response.sources().getFirst().pageNumber()).isEqualTo(2);
    }

    @Test
    void should_filter_chunks_below_threshold_before_building_prompt() {
        when(ragProperties.getTopK()).thenReturn(5);
        when(ragProperties.getScoreThreshold()).thenReturn(0.8);
        when(embeddingClient.embed(anyString())).thenReturn(List.of(0.1, 0.2, 0.3));

        DocumentChunk belowThreshold = new DocumentChunk(
                "doc-1",
                0,
                1,
                "Đoạn dưới ngưỡng",
                Map.of("title", "Doc One"));
        DocumentChunk aboveThreshold = new DocumentChunk(
                "doc-1",
                1,
                1,
                "Đoạn đạt ngưỡng",
                Map.of("title", "Doc One"));

        when(vectorStore.searchWithScores(anyList(), anyInt(), anyString(), anyDouble()))
                .thenReturn(List.of(
                        new ScoredDocumentChunk(belowThreshold, 0.79),
                        new ScoredDocumentChunk(aboveThreshold, 0.91)));
        when(ollamaClient.chat(anyString()))
                .thenReturn("ok");

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);

        ChatResponse response = chatService.chat("Câu hỏi bất kỳ", USERNAME);

        verify(ollamaClient).chat(promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("Đoạn đạt ngưỡng");
        assertThat(promptCaptor.getValue()).doesNotContain("Đoạn dưới ngưỡng");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().getFirst().textSnippet()).isEqualTo("Đoạn đạt ngưỡng");
    }
}
