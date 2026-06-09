package com.jobtrack.service;

import com.jobtrack.entity.ApplicationDocument;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.enums.DocumentType;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.repository.ApplicationDocumentRepository;
import com.jobtrack.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApplicationDocumentService {

    private final ApplicationDocumentRepository documentRepository;
    private final JobApplicationRepository jobApplicationRepository;

    @Value("${jobtrack.upload-dir}")
    private String uploadDir;

    @Transactional
    public ApplicationDocument storeDocument(Long applicationId, MultipartFile file, DocumentType documentType) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Job Application", applicationId));

        // 1. File Size Verification (Max 10 MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File exceeds maximum size limit of 10MB");
        }

        // 2. File Format Verification (PDF, DOCX)
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File must have a valid filename");
        }

        String lowercaseName = originalFilename.toLowerCase();
        if (!lowercaseName.endsWith(".pdf") && !lowercaseName.endsWith(".docx")) {
            throw new IllegalArgumentException("Unsupported file type. Only PDF and DOCX are allowed.");
        }

        // 3. Safe Renaming & Sanitization
        String sanitizedOriginal = originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_");
        String storedFilename = documentType.name() + "_" + System.currentTimeMillis() + "_" + sanitizedOriginal;

        try {
            // Determine storage path: /uploads/application-{id}/
            Path appDir = Paths.get(uploadDir).resolve("application-" + applicationId);
            Files.createDirectories(appDir);
            Path targetPath = appDir.resolve(storedFilename);

            // 4. Overwrite/Replacement Logic for CV and COVER_LETTER
            if (documentType == DocumentType.CV || documentType == DocumentType.COVER_LETTER) {
                Optional<ApplicationDocument> existing = documentRepository
                        .findByJobApplicationIdAndDocumentType(applicationId, documentType);
                
                if (existing.isPresent()) {
                    ApplicationDocument oldDoc = existing.get();
                    Files.deleteIfExists(Paths.get(oldDoc.getFilePath()));
                    documentRepository.delete(oldDoc);
                    documentRepository.flush();
                }
            }

            // Copy file content
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // 5. Database Save
            ApplicationDocument doc = ApplicationDocument.builder()
                    .jobApplication(application)
                    .fileName(originalFilename)
                    .fileType(file.getContentType())
                    .documentType(documentType)
                    .filePath(targetPath.toString())
                    .build();

            return documentRepository.save(doc);

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + originalFilename, e);
        }
    }

    @Transactional(readOnly = true)
    public List<ApplicationDocument> getDocumentsByApplicationId(Long applicationId) {
        if (!jobApplicationRepository.existsById(applicationId)) {
            throw new ResourceNotFoundException("Job Application", applicationId);
        }
        return documentRepository.findByJobApplicationIdOrderByUploadedAtDesc(applicationId);
    }

    @Transactional(readOnly = true)
    public ApplicationDocument getDocument(Long documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    @Transactional(readOnly = true)
    public Resource loadDocumentAsResource(Long applicationId, Long documentId) {
        ApplicationDocument doc = getDocument(documentId);
        if (!doc.getJobApplication().getId().equals(applicationId)) {
            throw new IllegalArgumentException("Document does not belong to the specified application");
        }

        try {
            Path filePath = Paths.get(doc.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("File not found or not readable: " + doc.getFileName());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("File path is malformed for document: " + doc.getFileName(), e);
        }
    }

    @Transactional
    public void deleteDocument(Long applicationId, Long documentId) {
        ApplicationDocument doc = getDocument(documentId);
        if (!doc.getJobApplication().getId().equals(applicationId)) {
            throw new IllegalArgumentException("Document does not belong to the specified application");
        }

        try {
            Files.deleteIfExists(Paths.get(doc.getFilePath()));
        } catch (IOException e) {
            // Log warning but proceed with DB deletion
            System.err.println("Could not delete file from disk: " + doc.getFilePath());
        }

        documentRepository.delete(doc);
    }

    @Transactional
    public void deleteApplicationDocuments(Long applicationId) {
        // Clean up disk files
        List<ApplicationDocument> docs = documentRepository.findByJobApplicationIdOrderByUploadedAtDesc(applicationId);
        for (ApplicationDocument doc : docs) {
            try {
                Files.deleteIfExists(Paths.get(doc.getFilePath()));
            } catch (IOException e) {
                System.err.println("Failed to delete document file on application cascade deletion: " + doc.getFilePath());
            }
        }

        // Delete the parent folder
        try {
            Path appDir = Paths.get(uploadDir).resolve("application-" + applicationId);
            Files.deleteIfExists(appDir);
        } catch (IOException e) {
            System.err.println("Failed to delete application folder on cascade deletion: application-" + applicationId);
        }
    }
}
