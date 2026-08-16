package vn.edu.uet.chatbot.rerank;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.uet.chatbot.config.RagProperties;
import vn.edu.uet.chatbot.store.dto.ScoredDocumentChunk;

import java.util.*;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class LexicalHybridReranker implements DocumentReranker {

    private final RagProperties ragProperties;
    private static final Pattern WORD_SPLIT = Pattern.compile("[\\p{Punct}\\s]+");

    // Từ nối tiếng Việt phổ biến (stop words)
    private static final Set<String> STOP_WORDS = Set.of(
            "là", "của", "và", "những", "các", "cho", "về", "có", "được", "trong", "với",
            "ở", "này", "đó", "thì", "mà", "khi", "như", "đã", "sẽ", "đang", "hay", "hoặc",
            "để", "nếu", "giữa", "qua", "sau", "trước", "từ", "lúc", "có", "chứng", "không",
            "cũng", "chỉ", "mới", "hôm", "ngày", "năm", "tháng", "tuần", "giờ", "phút", "giây");

    @Override
    public List<ScoredDocumentChunk> rerank(String query, List<ScoredDocumentChunk> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        // Nếu không bật rerank hoặc số ứng viên <= topK, trả về luôn
        if (!ragProperties.isRerankEnabled() || candidates.size() <= topK) {
            return candidates.stream().limit(topK).toList();
        }

        double vectorWeight = ragProperties.getRerankVectorWeight();
        double lexicalWeight = ragProperties.getRerankLexicalWeight();

        // Chuẩn hóa câu hỏi và bóc tách từ khóa
        String normalizedQuery = query.toLowerCase().trim();
        List<String> queryKeywords = extractKeywords(normalizedQuery);

        List<ScoredDocumentChunk> rerankedList = new ArrayList<>();

        for (ScoredDocumentChunk candidate : candidates) {
            String content = candidate.chunk().content();
            if (content == null || content.isBlank()) {
                continue;
            }

            double vectorScore = candidate.score();
            double lexicalScore = calculateLexicalScore(normalizedQuery, queryKeywords, content.toLowerCase());

            // Công thức tính điểm lai (Hybrid Score)
            // Final Score = (0.7 * Vector Score) + (0.3 * Lexical Score)
            double hybridScore = (vectorWeight * vectorScore) + (lexicalWeight * lexicalScore);

            // Làm tròn 4 chữ số thập phân
            double finalScore = Math.round(hybridScore * 10000.0) / 10000.0;

            rerankedList.add(new ScoredDocumentChunk(candidate.chunk(), finalScore));
        }

        // Sắp xếp điểm giảm dần và lấy Top-K
        rerankedList.sort((a, b) -> Double.compare(b.score(), a.score()));

        List<ScoredDocumentChunk> finalTopK = rerankedList.stream().limit(topK).toList();

        log.debug("Reranked {} candidates -> top {} (Best score: {})",
                candidates.size(), finalTopK.size(),
                finalTopK.isEmpty() ? 0.0 : finalTopK.get(0).score());

        return finalTopK;
    }

    /**
     * Tính toán điểm Lexical dựa trên:
     * 1. Độ bao phủ từ khóa (Keyword Coverage): 50%
     * 2. Mật độ tần suất từ khóa (Frequency Density): 20%
     * 3. Khớp cụm từ chính xác (Exact Phrase Match): 30%
     */
    private double calculateLexicalScore(String rawQuery, List<String> queryKeywords, String chunkText) {
        if (queryKeywords.isEmpty() || chunkText.isBlank()) {
            return 0.0;
        }

        // 1. Kiểm tra khớp cụm từ chính xác (query là một cụm từ dài)
        double exactMatchBonus = 0.0;
        if (rawQuery.length() > 3 && chunkText.contains(rawQuery)) {
            exactMatchBonus = 1.0;
        }

        // 2. Tính số lượng từ khóa xuất hiện trong đoạn văn
        long matchedCount = queryKeywords.stream()
                .filter(chunkText::contains)
                .count();

        double keywordCoverage = (double) matchedCount / queryKeywords.size();

        // 3. Tính mật độ xuất hiện của từ khóa
        int totalOccurrences = 0;
        for (String kw : queryKeywords) {
            totalOccurrences += countOccurrences(chunkText, kw);
        }
        // Normalize theo số từ khóa
        double frequencyDensity = Math.min(1.0, totalOccurrences / (double) (queryKeywords.size() * 3));

        // Kết hợp: 50% Coverage + 20% Frequency + 30% Exact Match
        return (keywordCoverage * 0.5) + (frequencyDensity * 0.2) + (exactMatchBonus * 0.3);
    }

    /**
     * Bóc tách từ khóa từ câu hỏi (loại bỏ stop words, giữ lại từ >= 2 ký tự)
     */
    private List<String> extractKeywords(String text) {
        String[] tokens = WORD_SPLIT.split(text);
        List<String> keywords = new ArrayList<>();
        for (String t : tokens) {
            String trimmed = t.trim();
            // Giữ lại từ có độ dài >= 2 và không phải stop word
            if (trimmed.length() >= 2 && !STOP_WORDS.contains(trimmed)) {
                keywords.add(trimmed);
            }
        }
        return keywords;
    }

    /**
     * Đếm số lần xuất hiện của một từ trong văn bản
     */
    private int countOccurrences(String text, String word) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(word, idx)) != -1) {
            count++;
            idx += word.length();
        }
        return count;
    }
}
