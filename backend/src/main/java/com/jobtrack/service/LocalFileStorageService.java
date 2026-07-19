package com.jobtrack.service;

import com.jobtrack.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class LocalFileStorageService implements DocumentStorageService {

    private final Path rootLocation;

    public LocalFileStorageService(String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(Long applicationId, MultipartFile file, DocumentType documentType, String storedFilename) throws IOException {
        Path targetDir = rootLocation.resolve("application-" + applicationId);
        Files.createDirectories(targetDir);
        Path targetPath = targetDir.resolve(storedFilename);
        
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Return relative path key for storage
        return "application-" + applicationId + "/" + storedFilename;
    }

    @Override
    public InputStream loadAsStream(String storageRef) throws IOException {
        Path path = Paths.get(storageRef);
        if (!path.isAbsolute()) {
            path = rootLocation.resolve(storageRef).normalize();
        }
        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new IOException("File not found or not readable at: " + path);
        }
        return Files.newInputStream(path);
    }

    @Override
    public void delete(String storageRef) throws IOException {
        Path path = Paths.get(storageRef);
        if (!path.isAbsolute()) {
            path = rootLocation.resolve(storageRef).normalize();
        }
        Files.deleteIfExists(path);
    }
}
