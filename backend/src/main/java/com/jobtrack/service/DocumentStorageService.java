package com.jobtrack.service;

import com.jobtrack.enums.DocumentType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;

public interface DocumentStorageService {
    String store(Long applicationId, MultipartFile file, DocumentType documentType, String storedFilename) throws IOException;
    InputStream loadAsStream(String storageRef) throws IOException;
    void delete(String storageRef) throws IOException;
}
