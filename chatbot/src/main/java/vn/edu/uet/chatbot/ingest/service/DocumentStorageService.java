package vn.edu.uet.chatbot.ingest.service;

import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.uet.chatbot.config.MinioProperties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

@Service
@Slf4j
@RequiredArgsConstructor
public class DocumentStorageService {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    public StoredDocument storeSourceFile(String documentId, MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = ".pdf";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        String objectName = documentId + "/source" + extension;

        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .stream(is, file.getSize(), -1)
                            .contentType(
                                    file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                            .build());
            log.info("Đã upload file lên MinIO: bucket={}, object={}", minioProperties.getBucket(), objectName);
            return new StoredDocument(objectName, file.getSize());
        } catch (Exception ex) {
            log.error("Lỗi upload lên MinIO: {}", ex.getMessage(), ex);
            throw new IOException("Không thể upload file lên MinIO: " + ex.getMessage(), ex);
        }
    }

    public Path resolveSourceFile(String storedFilePath) {
        if (storedFilePath == null || storedFilePath.isBlank()) {
            return null;
        }
        return Path.of(storedFilePath);
    }

    public boolean exists(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return false;
        }
        try {
            minioClient.statObject(
                    StatObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectName).build());
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public File downloadToTempFile(String objectName) throws IOException {
        if (!exists(objectName)) {
            throw new IOException("Không tìm thấy object trên MinIO: " + objectName);
        }

        String suffix = ".tmp";
        if (objectName.contains(".")) {
            suffix = objectName.substring(objectName.lastIndexOf("."));
        }

        File tempFile = File.createTempFile("minio-ingest-", suffix);
        tempFile.deleteOnExit();

        try (InputStream is = minioClient
                .getObject(GetObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectName).build());
                FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            return tempFile;
        } catch (Exception ex) {
            throw new IOException("Không thể tải file từ MinIO: " + ex.getMessage(), ex);
        }
    }

    public boolean deleteDocument(String documentId) throws IOException {
        try {
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .prefix(documentId + "/")
                            .recursive(true)
                            .build());

            boolean deletedAny = false;
            for (Result<Item> result : results) {
                Item item = result.get();
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(minioProperties.getBucket())
                        .object(item.objectName()).build());
                log.info("Đã xóa object khỏi MinIO: {}", item.objectName());
                deletedAny = true;
            }
            return deletedAny;
        } catch (Exception ex) {
            log.error("Lỗi khi xóa file trên MinIO: {}", ex.getMessage(), ex);
            throw new IOException("Lỗi xóa file trên MinIO: " + ex.getMessage(), ex);
        }
    }

    public record StoredDocument(String path, long sizeBytes) {
    }
}
