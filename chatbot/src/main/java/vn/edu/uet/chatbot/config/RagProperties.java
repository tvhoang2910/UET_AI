package vn.edu.uet.chatbot.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public class RagProperties {

    private int topK = 5;
    private int chunkSize = 1200;
    private int chunkOverlap = 200;
    private double scoreThreshold = 0.6;
    private int embeddingBatchSize = 16;

    // --- Hybrid reranking (vector + lexical overlap) ---
    private boolean rerankEnabled = true;
    private int rerankCandidateMultiplier = 3;
    private double rerankVectorWeight = 0.7;
    private double rerankLexicalWeight = 0.3;

    // --- Query condensation cho chat có lịch sử ---
    private boolean alwaysCondenseWithHistory = true;

    /**
     * Validate cấu hình ngay lúc khởi động thay vì đợi tới lần ingest đầu tiên
     * mới phát hiện lỗi (trước đây IllegalStateException chỉ được ném ra bên
     * trong SimpleTextChunker.chunk(), nghĩa là app có thể chạy cả ngày với
     * cấu hình sai trước khi có người upload tài liệu đầu tiên).
     */
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

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public double getScoreThreshold() {
        return scoreThreshold;
    }

    public void setScoreThreshold(double scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

    public int getEmbeddingBatchSize() {
        return embeddingBatchSize;
    }

    public void setEmbeddingBatchSize(int embeddingBatchSize) {
        this.embeddingBatchSize = embeddingBatchSize;
    }

    public boolean isRerankEnabled() {
        return rerankEnabled;
    }

    public void setRerankEnabled(boolean rerankEnabled) {
        this.rerankEnabled = rerankEnabled;
    }

    public int getRerankCandidateMultiplier() {
        return rerankCandidateMultiplier;
    }

    public void setRerankCandidateMultiplier(int rerankCandidateMultiplier) {
        this.rerankCandidateMultiplier = rerankCandidateMultiplier;
    }

    public double getRerankVectorWeight() {
        return rerankVectorWeight;
    }

    public void setRerankVectorWeight(double rerankVectorWeight) {
        this.rerankVectorWeight = rerankVectorWeight;
    }

    public double getRerankLexicalWeight() {
        return rerankLexicalWeight;
    }

    public void setRerankLexicalWeight(double rerankLexicalWeight) {
        this.rerankLexicalWeight = rerankLexicalWeight;
    }

    public boolean isAlwaysCondenseWithHistory() {
        return alwaysCondenseWithHistory;
    }

    public void setAlwaysCondenseWithHistory(boolean alwaysCondenseWithHistory) {
        this.alwaysCondenseWithHistory = alwaysCondenseWithHistory;
    }
}