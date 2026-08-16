package vn.edu.uet.chatbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StopWatch;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.edu.uet.chatbot.cache.SemanticCacheService;
import vn.edu.uet.chatbot.config.RagProperties;
import vn.edu.uet.chatbot.dto.ChatResponse;
import vn.edu.uet.chatbot.dto.ChatSource;
import vn.edu.uet.chatbot.entity.ChatMessageEntity;
import vn.edu.uet.chatbot.entity.ChatSessionEntity;
import vn.edu.uet.chatbot.ingest.model.DocumentCategory;
import vn.edu.uet.chatbot.prompt.ChatPromptBuilder;
import vn.edu.uet.chatbot.repository.ChatMessageRepository;
import vn.edu.uet.chatbot.repository.ChatSessionRepository;
import vn.edu.uet.chatbot.rerank.DocumentReranker;
import vn.edu.uet.chatbot.router.QueryCategoryRouter;
import vn.edu.uet.chatbot.store.QdrantVectorStore;
import vn.edu.uet.chatbot.store.dto.ScoredDocumentChunk;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatService {
    private static final String NO_INFO_ANSWER = "Không có thông tin trong tài liệu được cung cấp.";
    private static final Set<String> ANAPHORA = Set.of("nó", "đó", "này", "kia", "vậy", "tiếp", "cái đó", "cái này",
            "như trên", "bên trên", "em nó");

    private final EmbeddingModel embeddingModel;
    private final QdrantVectorStore vectorStore;
    private final ChatModel chatModel;
    private final RagProperties ragProperties;
    private final ChatPromptBuilder promptBuilder;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentReranker documentReranker;
    private final QueryCategoryRouter categoryRouter;
    private final SemanticCacheService semanticCacheService;
    private final PrerequisiteTracker prerequisiteTracker;
    private final Executor taskExecutor;

    public ChatResponse chat(String question, String requestUsername) {
        long totalStart = System.nanoTime();
        var vec = toDoubleList(embeddingModel.embed(question));

        var cached = semanticCacheService.findSimilar(vec);
        if (cached.isPresent()) {
            log.info("Chat semantic cache HIT total={}ms", elapsedMillis(totalStart));
            return new ChatResponse(null, cached.get().answer(), cached.get().sources());
        }

        List<ScoredDocumentChunk> retrieved = retrieve(question, vec, requestUsername);
        if (retrieved.isEmpty()) {
            return new ChatResponse(null, NO_INFO_ANSWER, List.of());
        }

        List<ScoredDocumentChunk> merged = mergeAdjacentChunksByPage(retrieved);
        var ctx = buildRetrievalContext(merged);
        if (ctx.context().isBlank()) {
            return new ChatResponse(null, NO_INFO_ANSWER, ctx.sources());
        }

        String prereqInfo = prerequisiteTracker.extractPrerequisiteChainSummary(question, merged);
        String fullContext = prereqInfo.isEmpty() ? ctx.context() : prereqInfo + "\n" + ctx.context();
        String answer = chatModel.call(promptBuilder.build(fullContext, question, NO_INFO_ANSWER));

        semanticCacheService.put(vec, question, answer, ctx.sources());
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
            try {
                String rewritten = chatModel
                        .call(promptBuilder.buildQueryCondensePrompt(historyText(historyEntities), question))
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
        var vec = toDoubleList(embeddingModel.embed(standaloneQuestion));
        List<ScoredDocumentChunk> retrieved = retrieve(standaloneQuestion, vec, requestUsername);
        List<ScoredDocumentChunk> merged = mergeAdjacentChunksByPage(retrieved);
        var ctx = buildRetrievalContext(merged);

        saveMessage(sessionId, "user", question);

        if (retrieved.isEmpty() || ctx.context().isBlank()) {
            UUID assistantMsgId = saveMessageAndReturnId(sessionId, "assistant", NO_INFO_ANSWER).getId();
            log.info("Chat total={}ms chunks={}", elapsedMillis(totalStart), ctx.sources().size());
            return new ChatResponse(assistantMsgId, NO_INFO_ANSWER, ctx.sources());
        }

        String prereqInfo = prerequisiteTracker.extractPrerequisiteChainSummary(standaloneQuestion, merged);
        String fullContext = prereqInfo.isEmpty() ? ctx.context() : prereqInfo + "\n" + ctx.context();

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptBuilder.buildSystemPrompt(fullContext, NO_INFO_ANSWER)));
        for (var h : historyEntities) {
            messages.add(toSpringAiMessage(h));
        }
        messages.add(new UserMessage(question));

        String answer = chatModel.call(new Prompt(messages)).getResult().getOutput().getText();
        ChatMessageEntity assistantMsg = saveMessageAndReturnId(sessionId, "assistant", answer);

        log.info("Chat total={}ms chunks={}", elapsedMillis(totalStart), ctx.sources().size());
        return new ChatResponse(assistantMsg.getId(), answer, ctx.sources());
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
                        String rewritten = chatModel
                                .call(promptBuilder.buildQueryCondensePrompt(historyText(historyEntities), question))
                                .trim().replaceAll("^\"|\"$", "").trim();
                        if (!rewritten.isBlank()) {
                            standaloneQuestion = rewritten;
                        }
                    } catch (Exception ex) {
                        log.warn("Condense fail, dùng câu gốc: {}", ex.getMessage());
                    }
                }

                var vec = toDoubleList(embeddingModel.embed(standaloneQuestion));
                var cached = semanticCacheService.findSimilar(vec);
                if (cached.isPresent()) {
                    saveMessage(sessionId, "user", question);
                    ChatMessageEntity assistantMsg = saveMessageAndReturnId(sessionId, "assistant",
                            cached.get().answer());
                    emitter.send(SseEmitter.event().name("token").data(cached.get().answer()));
                    emitter.send(SseEmitter.event().name("sources").data(cached.get().sources()));
                    emitter.send(
                            SseEmitter.event().name("done").data("{\"messageId\":\"" + assistantMsg.getId() + "\"}"));
                    emitter.complete();
                    SecurityContextHolder.clearContext();
                    return;
                }

                List<ScoredDocumentChunk> retrieved = retrieve(standaloneQuestion, vec, requestUsername);
                List<ScoredDocumentChunk> merged = mergeAdjacentChunksByPage(retrieved);
                var ctx = buildRetrievalContext(merged);

                saveMessage(sessionId, "user", question);

                if (retrieved.isEmpty() || ctx.context().isBlank()) {
                    ChatMessageEntity assistantMsg = saveMessageAndReturnId(sessionId, "assistant", NO_INFO_ANSWER);
                    emitter.send(SseEmitter.event().name("token").data(NO_INFO_ANSWER));
                    emitter.send(SseEmitter.event().name("sources").data(ctx.sources()));
                    emitter.send(
                            SseEmitter.event().name("done").data("{\"messageId\":\"" + assistantMsg.getId() + "\"}"));
                    emitter.complete();
                    SecurityContextHolder.clearContext();
                    return;
                }

                String prereqInfo = prerequisiteTracker.extractPrerequisiteChainSummary(standaloneQuestion, merged);
                String fullContext = prereqInfo.isEmpty() ? ctx.context() : prereqInfo + "\n" + ctx.context();

                List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
                messages.add(new SystemMessage(promptBuilder.buildSystemPrompt(fullContext, NO_INFO_ANSWER)));
                for (var h : historyEntities) {
                    messages.add(toSpringAiMessage(h));
                }
                messages.add(new UserMessage(question));

                StringBuilder sb = new StringBuilder();
                chatModel.stream(new Prompt(messages))
                        .doFinally(signalType -> SecurityContextHolder.clearContext())
                        .subscribe(
                                chatResponse -> {
                                    String token = chatResponse.getResult().getOutput().getText();
                                    if (token == null || token.isEmpty()) {
                                        return;
                                    }
                                    sb.append(token);
                                    try {
                                        emitter.send(SseEmitter.event().name("token").data(token));
                                    } catch (IOException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                },
                                error -> {
                                    SecurityContextHolder.clearContext();
                                    emitter.completeWithError(error);
                                },
                                () -> {
                                    try {
                                        String finalAns = sb.toString().isBlank() ? NO_INFO_ANSWER : sb.toString();
                                        ChatMessageEntity assistantMsg = saveMessageAndReturnId(sessionId, "assistant",
                                                finalAns);
                                        semanticCacheService.put(vec, question, finalAns, ctx.sources());
                                        emitter.send(SseEmitter.event().name("sources").data(ctx.sources()));
                                        emitter.send(SseEmitter.event().name("done")
                                                .data("{\"messageId\":\"" + assistantMsg.getId() + "\"}"));
                                        emitter.complete();
                                    } catch (IOException ex) {
                                        emitter.completeWithError(ex);
                                    }
                                });
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    public List<ChatSource> inspect(String question, String requestUsername) {
        var vec = toDoubleList(embeddingModel.embed(question));
        return buildRetrievalContext(mergeAdjacentChunksByPage(retrieve(question, vec, requestUsername))).sources();
    }

    private List<ScoredDocumentChunk> retrieve(String question, String requestUsername) {
        var vec = toDoubleList(embeddingModel.embed(question));
        return retrieve(question, vec, requestUsername);
    }

    private List<ScoredDocumentChunk> retrieve(String question, List<Double> vec, String requestUsername) {
        StopWatch sw = new StopWatch("chat-retrieve");
        sw.start("vector-search");

        double threshold = ragProperties.getScoreThreshold();
        int topK = ragProperties.getTopK();
        int candidateLimit = ragProperties.isRerankEnabled()
                ? topK * Math.max(1, ragProperties.getRerankCandidateMultiplier())
                : topK;

        Optional<DocumentCategory> detectedCat = categoryRouter.detectCategory(question);
        String categoryFilter = detectedCat.map(Enum::name).orElse(null);

        var candidates = vectorStore.searchWithScores(vec, candidateLimit, requestUsername, threshold, categoryFilter)
                .stream()
                .filter(candidate -> candidate.score() >= threshold)
                .toList();

        if (candidates.isEmpty() && categoryFilter != null) {
            log.info("Không có kết quả trong category '{}', fallback tìm toàn bộ database", categoryFilter);
            candidates = vectorStore.searchWithScores(vec, candidateLimit, requestUsername, threshold, null)
                    .stream()
                    .filter(candidate -> candidate.score() >= threshold)
                    .toList();
        }

        sw.stop();
        long searchDuration = sw.getLastTaskTimeMillis();

        if (!ragProperties.isRerankEnabled() || candidates.size() <= topK) {
            log.info("Chat search (No rerank) threshold={} topK={} duration={}ms matched={}",
                    threshold, topK, searchDuration, candidates.size());
            return candidates;
        }

        sw.start("rerank");
        List<ScoredDocumentChunk> rerankedResults = documentReranker.rerank(question, candidates, topK);
        sw.stop();

        log.info("Chat retrieval (Hybrid Reranked) candidates={} -> finalTopK={} search={}ms rerank={}ms",
                candidates.size(), rerankedResults.size(), searchDuration, sw.getLastTaskTimeMillis());
        return rerankedResults;
    }

    private List<ScoredDocumentChunk> mergeAdjacentChunksByPage(List<ScoredDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        Map<String, ScoredDocumentChunk> mergedMap = new LinkedHashMap<>();
        for (var scored : chunks) {
            var chunk = scored.chunk();
            String key = chunk.documentId() + "_p" + (chunk.pageNumber() == null ? -1 : chunk.pageNumber());
            if (!mergedMap.containsKey(key)) {
                mergedMap.put(key, scored);
            } else {
                var existing = mergedMap.get(key);
                String combinedContent = existing.chunk().content() + "\n\n" + chunk.content();
                double maxScore = Math.max(existing.score(), scored.score());
                var newChunk = new vn.edu.uet.chatbot.ingest.model.DocumentChunk(
                        chunk.documentId(),
                        existing.chunk().chunkIndex(),
                        chunk.pageNumber(),
                        combinedContent,
                        existing.chunk().metadata());
                mergedMap.put(key, new ScoredDocumentChunk(newChunk, maxScore));
            }
        }
        return new ArrayList<>(mergedMap.values());
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

    private boolean needsCondensation(String question, boolean hasHistory) {
        if (!hasHistory)
            return false;
        if (ragProperties.isAlwaysCondenseWithHistory())
            return true;
        if (question == null || question.isBlank())
            return false;
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

    private String historyText(List<ChatMessageEntity> historyEntities) {
        StringBuilder historyText = new StringBuilder();
        for (ChatMessageEntity msg : historyEntities) {
            if ("user".equals(msg.getRole()) || "assistant".equals(msg.getRole())) {
                String roleLabel = "user".equals(msg.getRole()) ? "Người dùng" : "Trợ lý";
                historyText.append(roleLabel).append(": ").append(msg.getContent()).append("\n");
            }
        }
        return historyText.toString();
    }

    private org.springframework.ai.chat.messages.Message toSpringAiMessage(ChatMessageEntity message) {
        return "assistant".equals(message.getRole())
                ? new AssistantMessage(message.getContent())
                : new UserMessage(message.getContent());
    }

    private List<Double> toDoubleList(float[] vector) {
        List<Double> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add((double) value);
        }
        return result;
    }

    private long elapsedMillis(long start) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private record RetrievalContext(String context, List<ChatSource> sources) {
    }
}
