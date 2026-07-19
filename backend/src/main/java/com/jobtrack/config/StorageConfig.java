package com.jobtrack.config;

import com.jobtrack.service.DocumentStorageService;
import com.jobtrack.service.LocalFileStorageService;
import com.jobtrack.service.R2StorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.provider", havingValue = "r2")
    public S3Client s3Client(
            @Value("${r2.endpoint}") String endpoint,
            @Value("${r2.access-key-id}") String accessKey,
            @Value("${r2.secret-access-key}") String secretKey,
            @Value("${r2.region:auto}") String region) {

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.provider", havingValue = "r2")
    public DocumentStorageService r2StorageService(
            S3Client s3Client,
            @Value("${r2.bucket-name}") String bucketName) {
        return new R2StorageService(s3Client, bucketName);
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
    public DocumentStorageService localFileStorageService(
            @Value("${jobtrack.upload-dir:../uploads}") String uploadDir) {
        return new LocalFileStorageService(uploadDir);
    }
}
