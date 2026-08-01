package vn.edu.uet.chatbot.ingest.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.uet.chatbot.ingest.entity.DocumentIngestionEntity;
import vn.edu.uet.chatbot.ingest.model.DocumentIngestionJob;
import vn.edu.uet.chatbot.ingest.repository.DocumentIngestionRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentIngestionRegistry {

    private final DocumentIngestionRepository repository;

    @Transactional
    public DocumentIngestionJob createPending(String documentId, String title, String originalFilename) {
        return createPending(documentId, title, originalFilename, null, 0L, null, false);
    }

    @Transactional
    public DocumentIngestionJob createPending(
            String documentId,
            String title,
            String originalFilename,
            String storedFilePath,
            long fileSizeBytes) {
        return createPending(documentId, title, originalFilename, storedFilePath, fileSizeBytes, null, false);
    }

    @Transactional
    public DocumentIngestionJob createPending(
            String documentId,
            String title,
            String originalFilename,
            String storedFilePath,
            long fileSizeBytes,
            String owner,
            boolean isPublic) {
        DocumentIngestionEntity entity = DocumentIngestionEntity.pending(
                documentId,
                title,
                originalFilename,
                storedFilePath,
                fileSizeBytes,
                owner,
                isPublic);
        return repository.save(entity).toJob();
    }

    @Transactional(readOnly = true)
    public Optional<DocumentIngestionJob> findById(String documentId) {
        return repository.findById(documentId).map(DocumentIngestionEntity::toJob);
    }

    @Transactional(readOnly = true)
    public List<DocumentIngestionJob> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(DocumentIngestionEntity::toJob)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentIngestionJob> findAllForContext(String username, boolean isAdmin) {
        return repository.findAllForContext(username, isAdmin)
                .stream()
                .map(DocumentIngestionEntity::toJob)
                .toList();
    }

    @Transactional
    public DocumentIngestionJob markProcessing(String documentId) {
        DocumentIngestionEntity entity = repository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document job not found: " + documentId));
        entity.markProcessing();
        return repository.save(entity).toJob();
    }

    @Transactional
    public DocumentIngestionJob markDone(String documentId, int chunkCount) {
        DocumentIngestionEntity entity = repository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document job not found: " + documentId));
        entity.markDone(chunkCount);
        return repository.save(entity).toJob();
    }

    @Transactional
    public DocumentIngestionJob markFailed(String documentId, String errorMessage) {
        DocumentIngestionEntity entity = repository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document job not found: " + documentId));
        entity.markFailed(errorMessage);
        return repository.save(entity).toJob();
    }

    @Transactional
    public Optional<DocumentIngestionJob> delete(String documentId) {
        Optional<DocumentIngestionEntity> existing = repository.findById(documentId);
        existing.ifPresent(repository::delete);
        return existing.map(DocumentIngestionEntity::toJob);
    }

    @Transactional(readOnly = true)
    public Optional<DocumentIngestionEntity> findEntityById(String documentId) {
        return repository.findById(documentId);
    }

    @Transactional
    public DocumentIngestionJob updateChunkCount(String documentId, int chunkCount) {
        DocumentIngestionEntity entity = repository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document job not found: " + documentId));
        entity.updateChunkCount(chunkCount);
        return repository.save(entity).toJob();
    }
}
