package com.jobtrack.service;

import com.jobtrack.enums.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;

@RequiredArgsConstructor
public class R2StorageService implements DocumentStorageService {

    private final S3Client s3Client;
    private final String bucketName;

    @Override
    public String store(Long applicationId, MultipartFile file, DocumentType documentType, String storedFilename) throws IOException {
        String key = "application-" + applicationId + "/" + storedFilename;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        try (InputStream is = file.getInputStream()) {
            s3Client.putObject(putRequest, RequestBody.fromInputStream(is, file.getSize()));
        } catch (Exception e) {
            throw new IOException("Failed to upload object to R2: " + key, e);
        }

        return key;
    }

    @Override
    public InputStream loadAsStream(String storageRef) throws IOException {
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(storageRef)
                .build();

        try {
            return s3Client.getObject(getRequest);
        } catch (Exception e) {
            throw new IOException("Failed to load object from R2: " + storageRef, e);
        }
    }

    @Override
    public void delete(String storageRef) throws IOException {
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(storageRef)
                .build();

        try {
            s3Client.deleteObject(deleteRequest);
        } catch (Exception e) {
            throw new IOException("Failed to delete object from R2: " + storageRef, e);
        }
    }
}
