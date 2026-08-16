package vn.edu.uet.chatbot.dto;

import vn.edu.uet.chatbot.ingest.model.DocumentIngestionJob;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionStatus;
import vn.edu.uet.chatbot.ingest.model.DocumentCategory;

public record DocumentUploadResponse(
        String documentId,
        String title,
        String originalFilename,
        DocumentCategory category,
        DocumentIngestionStatus status,
        String message) {

    public static DocumentUploadResponse from(DocumentIngestionJob job, String message) {
        return new DocumentUploadResponse(
                job.documentId(),
                job.title(),
                job.originalFilename(),
                job.category(),
                job.status(),
                message);
    }
}
