package vn.edu.uet.chatbot.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private int topK = 5;
    private int chunkSize = 1200;
    private int chunkOverlap = 200;
    private double scoreThreshold = 0.6;
    private int embeddingBatchSize = 16;

    // --- Hybrid reranking (vector + lexical overlap) ---
    private boolean rerankEnabled = true;
    private int rerankCandidateMultiplier = 3;// Thay vì lấy 5 miếng, lấy dư ra 5 * 3 = 15 miếng, rồi chấm lại để chọn 5
                                              // đứa ngon nhất.
    private double rerankVectorWeight = 0.7;
    private double rerankLexicalWeight = 0.3;

    // --- Query condensation cho chat có lịch sử ---
    private boolean alwaysCondenseWithHistory = true;

    @PostConstruct
    public void validate() {
        if (chunkSize <= 0) {
            throw new IllegalStateException("rag.chunk-size phải > 0 (hiện tại: " + chunkSize + ")");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalStateException(
                    "rag.chunk-overlap phải >= 0 và nhỏ hơn chunk-size (chunkSize=" + chunkSize
                            + ", chunkOverlap=" + chunkOverlap + ")");
        }
        if (topK <= 0) {
            throw new IllegalStateException("rag.top-k phải > 0");
        }
        if (scoreThreshold < 0 || scoreThreshold > 1) {
            throw new IllegalStateException("rag.score-threshold phải nằm trong khoảng [0,1]");
        }
        if (embeddingBatchSize <= 0) {
            throw new IllegalStateException("rag.embedding-batch-size phải > 0");
        }
        if (rerankCandidateMultiplier <= 0) {
            throw new IllegalStateException("rag.rerank-candidate-multiplier phải > 0");
        }
        if (rerankVectorWeight < 0 || rerankLexicalWeight < 0) {
            throw new IllegalStateException("rag.rerank-vector-weight và rag.rerank-lexical-weight phải >= 0");
        }
    }

}