package vn.edu.uet.chatbot.rerank;

import vn.edu.uet.chatbot.store.dto.ScoredDocumentChunk;

import java.util.List;

public interface DocumentReranker {
    /**
     * Tái sắp xếp danh sách các đoạn văn bản ứng viên dựa trên câu hỏi
     *
     * @param query      Câu hỏi của người dùng
     * @param candidates Danh sách các đoạn văn bản lấy từ Vector Search
     * @param topK       Số lượng đoạn văn tối đa cần giữ lại
     * @return Danh sách Top-K đoạn văn có điểm số kết hợp cao nhất
     */
    List<ScoredDocumentChunk> rerank(String query, List<ScoredDocumentChunk> candidates, int topK);
}
