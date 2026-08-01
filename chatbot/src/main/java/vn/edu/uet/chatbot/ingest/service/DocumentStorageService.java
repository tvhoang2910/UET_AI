package vn.edu.uet.chatbot.ingest.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class DocumentStorageService {

    private final Path storageRoot;

    public DocumentStorageService(@Value("${documents.storage-dir:./data/documents}") String storageDir) {
        this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public StoredDocument storeSourceFile(String documentId, MultipartFile file) throws IOException {
        Path documentDir = storageRoot.resolve(documentId);
        Files.createDirectories(documentDir);

        Path sourceFile = documentDir.resolve("source.pdf");
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, sourceFile, StandardCopyOption.REPLACE_EXISTING);
        }

        return new StoredDocument(sourceFile.toString(), Files.size(sourceFile));
    }

    public Path resolveSourceFile(String storedFilePath) {
        if (storedFilePath == null || storedFilePath.isBlank()) {
            return null;
        }
        return Path.of(storedFilePath);
    }

    public boolean exists(String storedFilePath) {
        Path path = resolveSourceFile(storedFilePath);
        return path != null && Files.exists(path);
    }

    public boolean deleteDocument(String documentId) throws IOException {
        Path documentDir = storageRoot.resolve(documentId).normalize();
        if (!documentDir.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid document storage path");
        }
        if (!Files.exists(documentDir)) {
            return false;
        }

        try (var paths = Files.walk(documentDir)) {
            paths.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }

        return true;
    }

    public record StoredDocument(String path, long sizeBytes) {
    }
}
