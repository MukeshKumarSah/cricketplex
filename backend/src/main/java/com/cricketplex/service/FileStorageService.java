package com.cricketplex.service;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Uploads a file and returns the object key (e.g. "profile-pics/uuid.jpg").
     */
    public String uploadFile(MultipartFile file, String folder) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String objectName = folder + "/" + UUID.randomUUID() + extension;

            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(file.getContentType())
                                .build()
                );
            }

            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a file by its object key.
     */
    public void deleteFile(String objectKey) {
        try {
            if (objectKey == null || objectKey.isBlank()) return;

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Failed to delete file: {}", e.getMessage());
        }
    }

    /**
     * Gets a file stream from MinIO by object key.
     */
    public GetObjectResponse getFile(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectKey)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to get file: " + e.getMessage(), e);
        }
    }

    /**
     * Converts an object key to a backend-served URL.
     */
    public String buildFileUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return "";
        return baseUrl + "/api/files/" + objectKey;
    }
}
