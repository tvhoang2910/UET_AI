package vn.edu.uet.chatbot.store;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.util.StopWatch;
import vn.edu.uet.chatbot.config.QdrantProperties;
import vn.edu.uet.chatbot.ingest.model.DocumentChunk;
import vn.edu.uet.chatbot.store.dto.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class QdrantVectorStore implements VectorStore {
    private final QdrantProperties properties;
    private final RestClient restClient;

    public QdrantVectorStore(QdrantProperties properties) {
        this.properties = properties;
        var httpClient = java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofMillis(3000)).build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(30000));
        this.restClient = RestClient.builder().baseUrl(properties.getUrl()).requestFactory(factory).build();
    }

    @Override
    public void upsert(String documentId, List<DocumentChunk> chunks, List<List<Double>> vectors) {
        if (chunks.size() != vectors.size())
            throw new IllegalArgumentException("Chunks and vectors size mismatch");
        StopWatch sw = new StopWatch("qdrant-upsert-" + documentId);
        sw.start("build-payload");
        List<QdrantPoint> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            String pointId = java.util.UUID.nameUUIDFromBytes((documentId + "-" + chunk.chunkIndex()).getBytes())
                    .toString();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", documentId);
            payload.put("title", chunk.metadata().getOrDefault("title", documentId));
            payload.put("chunkIndex", chunk.chunkIndex());
            if (chunk.pageNumber() != null)
                payload.put("page", chunk.pageNumber());
            payload.put("text", chunk.content());
            payload.put("createdAt", Instant.now().toString());
            payload.putAll(chunk.metadata());
            points.add(new QdrantPoint(pointId, vectors.get(i), payload));
        }
        sw.stop();
        long buildMs = sw.getLastTaskTimeMillis();
        sw.start("request");
        restClient.put().uri("/collections/{collection}/points", properties.getCollection())
                .body(new QdrantUpsertRequest(points)).retrieve().toBodilessEntity();
        sw.stop();
        log.info("Qdrant upsert doc={} chunks={} build={}ms req={}ms", documentId, chunks.size(), buildMs,
                sw.getLastTaskTimeMillis());
    }

    public List<DocumentChunk> search(List<Double> queryVector, int topK) {
        return searchWithScores(queryVector, topK, null, 0.0).stream().map(ScoredDocumentChunk::chunk).toList();
    }

    public List<ScoredDocumentChunk> searchWithScores(List<Double> queryVector, int topK) {
        return searchWithScores(queryVector, topK, null, 0.0);
    }

    public List<ScoredDocumentChunk> searchWithScores(List<Double> queryVector, int topK, String reqUsername) {
        return searchWithScores(queryVector, topK, reqUsername, 0.0);
    }

    public List<ScoredDocumentChunk> searchWithScores(List<Double> queryVector, int topK, String reqUsername,
            double scoreThreshold) {
        StopWatch sw = new StopWatch("qdrant-search");
        sw.start("request");

        Map<String, Object> filter = null;
        var request = new QdrantSearchRequest(queryVector, topK, filter, true,
                scoreThreshold > 0 ? scoreThreshold : null);
        var response = restClient.post().uri("/collections/{collection}/points/search", properties.getCollection())
                .body(request).retrieve().body(QdrantSearchResponse.class);
        sw.stop();
        if (response == null || response.result() == null)
            return List.of();
        List<ScoredDocumentChunk> out = new ArrayList<>();
        for (var p : response.result()) {
            if (p.payload() == null)
                continue;
            String docId = (String) p.payload().getOrDefault("documentId", "");
            int idx = p.payload().get("chunkIndex") instanceof Number n ? n.intValue() : 0;
            Integer page = p.payload().get("page") instanceof Number n ? n.intValue() : null;
            String text = (String) p.payload().getOrDefault("text", "");
            out.add(new ScoredDocumentChunk(new DocumentChunk(docId, idx, page, text, new LinkedHashMap<>(p.payload())),
                    p.score()));
        }
        log.info("Qdrant search topK={} threshold={} duration={}ms results={}", topK, scoreThreshold,
                sw.getTotalTimeMillis(), out.size());
        return out;
    }

    @Override
    public void deleteByDocumentId(String documentId) {
        var req = new QdrantDeletePointsRequest(new QdrantDeletePointsRequest.QdrantFilter(List.of(
                new QdrantDeletePointsRequest.QdrantCondition("documentId",
                        new QdrantDeletePointsRequest.QdrantMatch(documentId)))));
        restClient.post().uri("/collections/{collection}/points/delete", properties.getCollection())
                .body(req).retrieve().toBodilessEntity();
    }
}