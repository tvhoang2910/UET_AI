package vn.edu.uet.chatbot.ingest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionJob;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionStatus;

import java.time.Instant;

@Entity
@Table(name = "document_ingestion_jobs")
public class DocumentIngestionEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String documentId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(length = 512)
    private String originalFilename;

    @Column(length = 1024)
    private String storedFilePath;

    @Column(nullable = false)
    private long fileSizeBytes;

    @Column(length = 255)
    private String owner;

    @Column(nullable = false)
    private boolean isPublic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentIngestionStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant finishedAt;

    @Lob
    private String errorMessage;

    @Column(nullable = false)
    private int chunkCount;

    protected DocumentIngestionEntity() {
    }

    public void updateChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
        this.updatedAt = java.time.Instant.now();
    }

    public static DocumentIngestionEntity pending(
            String documentId,
            String title,
            String originalFilename,
            String storedFilePath,
            long fileSizeBytes,
            String owner,
            boolean isPublic) {
        Instant now = Instant.now();
        DocumentIngestionEntity entity = new DocumentIngestionEntity();
        entity.documentId = documentId;
        entity.title = title;
        entity.originalFilename = originalFilename;
        entity.storedFilePath = storedFilePath;
        entity.fileSizeBytes = fileSizeBytes;
        entity.owner = owner;
        entity.isPublic = isPublic;
        entity.status = DocumentIngestionStatus.PENDING;
        entity.createdAt = now;
        entity.updatedAt = now;
        entity.finishedAt = null;
        entity.errorMessage = null;
        entity.chunkCount = 0;
        return entity;
    }

    public void markProcessing() {
        this.status = DocumentIngestionStatus.PROCESSING;
        this.updatedAt = Instant.now();
        this.finishedAt = null;
        this.errorMessage = null;
    }

    public void markDone(int chunkCount) {
        Instant now = Instant.now();
        this.status = DocumentIngestionStatus.DONE;
        this.updatedAt = now;
        this.finishedAt = now;
        this.errorMessage = null;
        this.chunkCount = chunkCount;
    }

    public void markFailed(String errorMessage) {
        Instant now = Instant.now();
        this.status = DocumentIngestionStatus.FAILED;
        this.updatedAt = now;
        this.finishedAt = now;
        this.errorMessage = errorMessage;
    }

    public DocumentIngestionJob toJob() {
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
                isPublic);
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
        if (status == null) {
            status = DocumentIngestionStatus.PENDING;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getTitle() {
        return title;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredFilePath() {
        return storedFilePath;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public DocumentIngestionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public String getOwner() {
        return owner;
    }

    public boolean isPublic() {
        return isPublic;
    }
}
