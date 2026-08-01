package vn.edu.uet.chatbot.dto;

import vn.edu.uet.chatbot.ingest.model.DocumentIngestionJob;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionStatus;
import java.time.Instant;

public record DocumentStatusResponse(
        String documentId,
        String title,
        String originalFilename,
        String storedFilePath,
        long fileSizeBytes,
        DocumentIngestionStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt,
        boolean sourceFileExists,
        boolean reindexable,
        String errorMessage,
        int chunkCount,
        String owner,
        boolean isPublic) {

    public static DocumentStatusResponse from(DocumentIngestionJob job, boolean sourceFileExists) {
        return new DocumentStatusResponse(
                job.documentId(),
                job.title(),
                job.originalFilename(),
                job.storedFilePath(),
                job.fileSizeBytes(),
                job.status(),
                job.createdAt(),
                job.updatedAt(),
                job.finishedAt(),
                sourceFileExists,
                sourceFileExists && job.storedFilePath() != null && !job.storedFilePath().isBlank(),
                job.errorMessage(),
                job.chunkCount(),
                job.owner(),
                job.isPublic());
    }
}