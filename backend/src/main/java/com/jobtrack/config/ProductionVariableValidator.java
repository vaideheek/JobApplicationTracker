package com.jobtrack.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@Slf4j
public class ProductionVariableValidator {

    @Value("${app.admin.username:${app.admin.email:}}")
    private String adminUsername;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${app.storage.provider:local}")
    private String storageProvider;

    @Value("${r2.endpoint:}")
    private String r2Endpoint;

    @Value("${r2.access-key-id:}")
    private String r2AccessKey;

    @Value("${r2.secret-access-key:}")
    private String r2SecretKey;

    @Value("${r2.bucket-name:}")
    private String r2BucketName;

    @PostConstruct
    public void validate() {
        log.info("Validating production environment variables...");

        if (adminUsername == null || adminUsername.isBlank()) {
            throw new IllegalStateException("Production startup failed: APP_ADMIN_EMAIL/APP_ADMIN_USERNAME environment variable is missing");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("Production startup failed: APP_ADMIN_PASSWORD environment variable is missing");
        }

        if (dbUrl == null || dbUrl.isBlank() || dbUrl.contains("localhost") || dbUrl.contains("127.0.0.1")) {
            throw new IllegalStateException("Production startup failed: SPRING_DATASOURCE_URL is empty or pointing to localhost");
        }

        if ("r2".equalsIgnoreCase(storageProvider)) {
            if (r2Endpoint == null || r2Endpoint.isBlank()) {
                throw new IllegalStateException("Production startup failed: R2_ENDPOINT environment variable is missing when storage provider is R2");
            }
            if (r2AccessKey == null || r2AccessKey.isBlank()) {
                throw new IllegalStateException("Production startup failed: R2_ACCESS_KEY_ID environment variable is missing when storage provider is R2");
            }
            if (r2SecretKey == null || r2SecretKey.isBlank()) {
                throw new IllegalStateException("Production startup failed: R2_SECRET_ACCESS_KEY environment variable is missing when storage provider is R2");
            }
            if (r2BucketName == null || r2BucketName.isBlank()) {
                throw new IllegalStateException("Production startup failed: R2_BUCKET_NAME environment variable is missing when storage provider is R2");
            }
        }

        log.info("Production environment variables successfully validated.");
    }
}
