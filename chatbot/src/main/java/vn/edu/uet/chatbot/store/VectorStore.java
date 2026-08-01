package vn.edu.uet.chatbot.store;

import vn.edu.uet.chatbot.ingest.model.DocumentChunk;

import java.util.List;

public interface VectorStore {
    void upsert(String documentId, List<DocumentChunk> chunks, List<List<Double>> vectors);

    void deleteByDocumentId(String documentId);
}
