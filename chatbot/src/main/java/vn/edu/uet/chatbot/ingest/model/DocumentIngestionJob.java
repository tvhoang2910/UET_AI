package vn.edu.uet.chatbot.ingest.model;

import java.time.Instant;

public record DocumentIngestionJob(
        String documentId,
        String title,
        String originalFilename,
        String storedFilePath,
        long fileSizeBytes,
        DocumentIngestionStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt,
        String errorMessage,
        int chunkCount,
        String owner,
        boolean isPublic,
        DocumentCategory category) {

    public static DocumentIngestionJob pending(String documentId, String title, String originalFilename) {
        return pending(documentId, title, originalFilename, null, 0L);
    }

    public static DocumentIngestionJob pending(
            String documentId,
            String title,
            String originalFilename,
            String storedFilePath,
            long fileSizeBytes) {
        return pending(documentId, title, originalFilename, storedFilePath, fileSizeBytes, null, false);
    }

    public static DocumentIngestionJob pending(
            String documentId,
            String title,
            String originalFilename,
            String storedFilePath,
            long fileSizeBytes,
            String owner,
            boolean isPublic) {
        return pending(documentId, title, originalFilename, storedFilePath, fileSizeBytes, owner, isPublic,
                DocumentCategory.KHAC);
    }

    public static DocumentIngestionJob pending(
            String documentId,
            String title,
            String originalFilename,
            String storedFilePath,
            long fileSizeBytes,
            String owner,
            boolean isPublic,
            DocumentCategory category) {
        Instant now = Instant.now();
        return new DocumentIngestionJob(
                documentId,
                title,
                originalFilename,
                storedFilePath,
                fileSizeBytes,
                DocumentIngestionStatus.PENDING,
                now,
                now,
                null,
                null,
                0,
                owner,
                isPublic,
                category == null ? DocumentCategory.KHAC : category);
    }

    public DocumentIngestionJob processing() {
        Instant now = Instant.now();
        return new DocumentIngestionJob(
                documentId,
                title,
                originalFilename,
                storedFilePath,
                fileSizeBytes,
                DocumentIngestionStatus.PROCESSING,
                createdAt,
                now,
                null,
                null,
                chunkCount,
                owner,
                isPublic,
                category);
    }

    public DocumentIngestionJob done(int chunkCount) {
        Instant now = Instant.now();
        return new DocumentIngestionJob(
                documentId,
                title,
                originalFilename,
                storedFilePath,
                fileSizeBytes,
                DocumentIngestionStatus.DONE,
                createdAt,
                now,
                now,
                null,
                chunkCount,
                owner,
                isPublic,
                category);
    }

    public DocumentIngestionJob failed(String errorMessage) {
        Instant now = Instant.now();
        return new DocumentIngestionJob(
                documentId,
                title,
                originalFilename,
                storedFilePath,
                fileSizeBytes,
                DocumentIngestionStatus.FAILED,
                createdAt,
                now,
                now,
                errorMessage,
                chunkCount,
                owner,
                isPublic,
                category);
    }

    public DocumentIngestionJob withSource(String storedFilePath, long fileSizeBytes) {
        return new DocumentIngestionJob(
                documentId,
                title,
                originalFilename,
                storedFilePath,
                fileSizeBytes,
                status,
                createdAt,
                updatedAt,
                finishedAt,
                errorMessage,
                chunkCount,
                owner,
                isPublic,
                category);
    }
}
