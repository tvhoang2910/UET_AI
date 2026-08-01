package vn.edu.uet.chatbot.dto;

import vn.edu.uet.chatbot.ingest.model.DocumentIngestionJob;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionStatus;

public record DocumentDeleteResponse(
        String documentId,
        String title,
        DocumentIngestionStatus previousStatus,
        boolean deletedFromRegistry,
        boolean deletedFromVectorStore,
        boolean deletedFromStorage,
        String message) {

    public static DocumentDeleteResponse success(
            DocumentIngestionJob job,
            boolean deletedFromRegistry,
            boolean deletedFromVectorStore,
            boolean deletedFromStorage) {
        return new DocumentDeleteResponse(
                job.documentId(),
                job.title(),
                job.status(),
                deletedFromRegistry,
                deletedFromVectorStore,
                deletedFromStorage,
                "Document deleted");
    }
}
