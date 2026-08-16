package vn.edu.uet.chatbot.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.uet.chatbot.dto.ChatSource;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
@Slf4j
public class SemanticCacheService {

    private static final double SIMILARITY_THRESHOLD = 0.95;
    private static final int MAX_CACHE_SIZE = 300;

    private final Deque<CachedItem> cache = new ConcurrentLinkedDeque<>();

    public record CachedResponse(String answer, List<ChatSource> sources) {
    }

    public Optional<CachedResponse> findSimilar(List<Double> queryVector) {
        if (queryVector == null || queryVector.isEmpty() || cache.isEmpty()) {
            return Optional.empty();
        }

        CachedItem bestMatch = null;
        double maxSimilarity = -1.0;

        for (CachedItem item : cache) {
            double similarity = cosineSimilarity(queryVector, item.vector);
            if (similarity > maxSimilarity) {
                maxSimilarity = similarity;
                bestMatch = item;
            }
        }

        if (bestMatch != null && maxSimilarity >= SIMILARITY_THRESHOLD) {
            log.info("🎯 SEMANTIC CACHE HIT! Độ tương đồng: {}% với câu hỏi gốc: '{}'",
                    Math.round(maxSimilarity * 1000.0) / 10.0, bestMatch.question);
            return Optional.of(new CachedResponse(bestMatch.answer, bestMatch.sources));
        }

        return Optional.empty();
    }

    public void put(List<Double> queryVector, String question, String answer, List<ChatSource> sources) {
        if (queryVector == null || answer == null || answer.isBlank()) {
            return;
        }

        while (cache.size() >= MAX_CACHE_SIZE) {
            cache.pollFirst();
        }

        cache.addLast(new CachedItem(queryVector, question, answer, sources, Instant.now()));
        log.debug("Đã lưu Semantic Cache cho câu hỏi: '{}'", question);
    }

    private double cosineSimilarity(List<Double> vecA, List<Double> vecB) {
        if (vecA.size() != vecB.size()) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vecA.size(); i++) {
            double a = vecA.get(i);
            double b = vecB.get(i);
            dotProduct += a * b;
            normA += a * a;
            normB += b * b;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record CachedItem(
            List<Double> vector,
            String question,
            String answer,
            List<ChatSource> sources,
            Instant createdAt) {
    }
}
