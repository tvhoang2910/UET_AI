package vn.edu.uet.chatbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.edu.uet.chatbot.client.OllamaClient;
import vn.edu.uet.chatbot.config.RagProperties;
import vn.edu.uet.chatbot.dto.ChatResponse;
import vn.edu.uet.chatbot.dto.ChatSource;
import vn.edu.uet.chatbot.dto.ollama.Message;
import vn.edu.uet.chatbot.embed.EmbeddingClient;
import vn.edu.uet.chatbot.entity.ChatMessageEntity;
import vn.edu.uet.chatbot.entity.ChatSessionEntity;
import vn.edu.uet.chatbot.prompt.ChatPromptBuilder;
import vn.edu.uet.chatbot.repository.ChatMessageRepository;
import vn.edu.uet.chatbot.repository.ChatSessionRepository;
import vn.edu.uet.chatbot.rerank.DocumentReranker;
import vn.edu.uet.chatbot.store.QdrantVectorStore;
import vn.edu.uet.chatbot.store.dto.ScoredDocumentChunk;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static java.util.stream.Collectors.toList;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {
    private static final String NO_INFO_ANSWER = "Không có thông tin trong tài liệu được cung cấp.";
    private static final Set<String> ANAPHORA = Set.of("nó", "đó", "này", "kia", "vậy", "tiếp", "cái đó", "cái này",
            "như trên", "bên trên", "em nó");

    private final EmbeddingClient embeddingClient;
    private final QdrantVectorStore vectorStore;
    private final OllamaClient ollamaClient;
    private final RagProperties ragProperties;
    private final ChatPromptBuilder promptBuilder;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentReranker documentReranker;
    private final Executor taskExecutor;

    public ChatResponse chat(String question, String requestUsername) {
        long totalStart = System.nanoTime();
        List<ScoredDocumentChunk> retrieved = retrieve(question, requestUsername);
        if (retrieved.isEmpty()) {
            return new ChatResponse(null, NO_INFO_ANSWER, List.of());
        }
        var ctx = buildRetrievalContext(retrieved);
        if (ctx.context().isBlank()) {
            return new ChatResponse(null, NO_INFO_ANSWER, ctx.sources());
        }
        String answer = ollamaClient.chat(promptBuilder.build(ctx.context(), question, NO_INFO_ANSWER));
        log.info("Chat total={}ms chunks={}", elapsedMillis(totalStart), ctx.sources().size());
        return new ChatResponse(null, answer, ctx.sources());
    }

    @Transactional
    public ChatResponse chat(UUID sessionId, String question, String requestUsername) {
        ChatSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiên chat: " + sessionId));
        if (!session.getUsername().equals(requestUsername)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bạn không có quyền truy cập phiên chat này.");
        }

        List<ChatMessageEntity> historyEntities = messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId)
                .reversed();

        String standaloneQuestion = question;
        if (!historyEntities.isEmpty() && needsCondensation(question, !historyEntities.isEmpty())) {
            List<Message> condenseHistory = historyEntities.stream().map(h -> new Message(h.getRole(), h.getContent()))
                    .toList();
            try {
                String rewritten = ollamaClient.chat(promptBuilder.buildQueryCondensePrompt(condenseHistory, question))
                        .trim().replaceAll("^\"|\"$", "").trim();
                if (!rewritten.isBlank()) {
                    standaloneQuestion = rewritten;
                    log.info("Query rewritten: '{}' -> '{}'", question, standaloneQuestion);
                }
            } catch (Exception ex) {
                log.warn("Condense fail, dùng câu gốc: {}", ex.getMessage());
            }
        }

        long totalStart = System.nanoTime();
        List<ScoredDocumentChunk> retrieved = retrieve(standaloneQuestion, requestUsername);

        UUID assistantMsgId;
        var ctx = buildRetrievalContext(retrieved);

        saveMessage(sessionId, "user", question);

        if (retrieved.isEmpty() || ctx.context().isBlank()) {
            assistantMsgId = saveMessageAndReturnId(sessionId, "assistant", NO_INFO_ANSWER).getId();
            log.info("Chat total={}ms chunks={}", elapsedMillis(totalStart), ctx.sources().size());
            return new ChatResponse(assistantMsgId, NO_INFO_ANSWER, ctx.sources());
        }

        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", promptBuilder.buildSystemPrompt(ctx.context(), NO_INFO_ANSWER)));
        for (var h : historyEntities) {
            messages.add(new Message(h.getRole(), h.getContent()));
        }
        messages.add(new Message("user", question));

        String answer = ollamaClient.chat(messages);
        ChatMessageEntity assistantMsg = saveMessageAndReturnId(sessionId, "assistant", answer);
        assistantMsgId = assistantMsg.getId();

        log.info("Chat total={}ms chunks={}", elapsedMillis(totalStart), ctx.sources().size());
        return new ChatResponse(assistantMsgId, answer, ctx.sources());
    }

    private boolean needsCondensation(String question, boolean hasHistory) {
        if (!hasHistory) {
            return false;
        }
        if (ragProperties.isAlwaysCondenseWithHistory()) {
            return true;
        }
        if (question == null || question.isBlank()) {
            return false;
        }
        String lower = question.toLowerCase();
        return ANAPHORA.stream().anyMatch(lower::contains);
    }

    private ChatMessageEntity saveMessageAndReturnId(UUID sessionId, String role, String content) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        return messageRepository.save(msg);
    }

    private void saveMessage(UUID sessionId, String role, String content) {
        saveMessageAndReturnId(sessionId, role, content);
    }

    public SseEmitter chatStream(UUID sessionId, String question, String requestUsername) {
        SseEmitter emitter = new SseEmitter(300_000L);

        SecurityContext context = SecurityContextHolder.getContext();

        taskExecutor.execute(() -> {
            SecurityContextHolder.setContext(context);
            try {
                ChatSessionEntity session = sessionRepository.findById(sessionId)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiên chat: " + sessionId));

                if (!session.getUsername().equals(requestUsername)) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "Bạn không có quyền truy cập phiên chat này.");
                }

                List<ChatMessageEntity> historyEntities = messageRepository
                        .findTop10BySessionIdOrderByCreatedAtDesc(sessionId)
                        .reversed();

                String standaloneQuestion = question;
                if (!historyEntities.isEmpty() && needsCondensation(question, !historyEntities.isEmpty())) {
                    try {
                        List<Message> condenseHistory = historyEntities.stream()
                                .map(h -> new Message(h.getRole(), h.getContent()))
                                .toList();
                        String rewritten = ollamaClient
                                .chat(promptBuilder.buildQueryCondensePrompt(condenseHistory, question))
                                .trim()
                                .replaceAll("^\"|\"$", "")
                                .trim();
                        if (!rewritten.isBlank()) {
                            standaloneQuestion = rewritten;
                        }
                    } catch (Exception ex) {
                        log.warn("Condense fail, dùng câu gốc: {}", ex.getMessage());
                    }
                }

                List<ScoredDocumentChunk> retrieved = retrieve(standaloneQuestion, requestUsername);
                var ctx = buildRetrievalContext(retrieved);

                saveMessage(sessionId, "user", question);

                if (retrieved.isEmpty() || ctx.context().isBlank()) {
                    ChatMessageEntity assistantMsg = saveMessageAndReturnId(sessionId, "assistant", NO_INFO_ANSWER);
                    emitter.send(SseEmitter.event().name("token").data(NO_INFO_ANSWER));
                    emitter.send(SseEmitter.event().name("sources").data(ctx.sources()));
                    emitter.send(
                            SseEmitter.event().name("done").data("{\"messageId\":\"" + assistantMsg.getId() + "\"}"));
                    emitter.complete();
                    return;
                }

                List<Message> messages = new ArrayList<>();
                messages.add(new Message("system", promptBuilder.buildSystemPrompt(ctx.context(), NO_INFO_ANSWER)));
                for (var h : historyEntities) {
                    messages.add(new Message(h.getRole(), h.getContent()));
                }
                messages.add(new Message("user", question));

                StringBuilder sb = new StringBuilder();
                ollamaClient.chatStream(messages, token -> {
                    sb.append(token);
                    try {
                        emitter.send(SseEmitter.event().name("token").data(token));
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });

                String finalAns = sb.toString().isBlank() ? NO_INFO_ANSWER : sb.toString();
                ChatMessageEntity assistantMsg = saveMessageAndReturnId(sessionId, "assistant", finalAns);

                emitter.send(SseEmitter.event().name("sources").data(ctx.sources()));
                emitter.send(SseEmitter.event().name("done").data("{\"messageId\":\"" + assistantMsg.getId() + "\"}"));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        return emitter;
    }

    public List<ChatSource> inspect(String question, String requestUsername) {
        return buildRetrievalContext(retrieve(question, requestUsername)).sources();
    }

    /**
     * Hàm retrieve đã được tối ưu với Hybrid Reranking (Vector + Lexical)
     * 
     * Quy trình:
     * 1. Lấy embedding của câu hỏi
     * 2. Vector Search lấy ra topK * multiplier ứng viên từ Qdrant (default: 15)
     * 3. Nếu bật rerank: tính điểm lai (vector + lexical) và chọn top K tốt nhất
     * 4. Trả về danh sách top K đoạn văn cuối cùng
     */
    private List<ScoredDocumentChunk> retrieve(String question, String requestUsername) {
        StopWatch sw = new StopWatch("chat-retrieve");
        sw.start("embedding");
        var vec = embeddingClient.embed(question);
        sw.stop();

        sw.start("vector-search");
        double threshold = ragProperties.getScoreThreshold();
        int topK = ragProperties.getTopK();

        // Tính toán số lượng ứng viên cần lấy từ vector store
        // Nếu bật rerank: lấy topK * multiplier (default 5 * 3 = 15)
        // Nếu không bật rerank: lấy topK (default 5)
        int candidateLimit = ragProperties.isRerankEnabled()
                ? topK * Math.max(1, ragProperties.getRerankCandidateMultiplier())
                : topK;

        var candidates = vectorStore.searchWithScores(vec, candidateLimit, requestUsername, threshold)
                .stream()
                .filter(candidate -> candidate.score() >= threshold)
                .toList();
        sw.stop();
        long searchDuration = sw.getLastTaskTimeMillis();

        // Nếu không bật rerank hoặc số ứng viên <= topK thì trả về luôn (không cần
        // rerank)
        if (!ragProperties.isRerankEnabled() || candidates.size() <= topK) {
            log.info("Chat search (No rerank) threshold={} topK={} duration={}ms matched={}",
                    threshold, topK, searchDuration, candidates.size());
            return candidates;
        }

        // Chạy thuật toán Hybrid Reranking
        sw.start("rerank");
        List<ScoredDocumentChunk> rerankedResults = documentReranker.rerank(question, candidates, topK);
        sw.stop();

        log.info("Chat retrieval (Hybrid Reranked) candidates={} -> finalTopK={} search={}ms rerank={}ms",
                candidates.size(), rerankedResults.size(), searchDuration, sw.getLastTaskTimeMillis());

        return rerankedResults;
    }

    private RetrievalContext buildRetrievalContext(List<ScoredDocumentChunk> retrieved) {
        StringBuilder context = new StringBuilder();
        List<ChatSource> sources = new ArrayList<>();

        for (int i = 0; i < retrieved.size(); i++) {
            var scored = retrieved.get(i);
            var chunk = scored.chunk();
            String text = chunk.content();
            String title = (String) chunk.metadata().getOrDefault("title", chunk.documentId());
            Integer pageNumber = chunk.pageNumber();
            int sourceIndex = i + 1;

            if (text == null || text.isBlank()) {
                sources.add(new ChatSource(title, chunk.chunkIndex(), pageNumber, scored.score(), ""));
                continue;
            }

            context.append("--- [NGUỒN ").append(sourceIndex)
                    .append(" | TÀI LIỆU: \"").append(title).append("\"")
                    .append(pageNumber == null ? "" : " | TRANG: " + pageNumber)
                    .append("] ---\n")
                    .append(text.trim())
                    .append("\n\n");

            String snippet = text.length() > 400 ? text.substring(0, 400) + "..." : text;
            sources.add(new ChatSource(title, chunk.chunkIndex(), pageNumber, scored.score(), snippet));
        }

        return new RetrievalContext(context.toString().trim(), sources);
    }

    private long elapsedMillis(long start) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private record RetrievalContext(String context, List<ChatSource> sources) {
    }
}
