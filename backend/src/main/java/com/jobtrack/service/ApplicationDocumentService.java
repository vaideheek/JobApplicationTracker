package com.jobtrack.service;

import com.jobtrack.dto.ApplicationDocumentResponse;
import com.jobtrack.entity.ApplicationDocument;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.enums.DocumentType;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.repository.ApplicationDocumentRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationDocumentService {

    private final ApplicationDocumentRepository documentRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final DocumentStorageService storageService;
    private final CurrentUserService currentUserService;

    @Transactional
    public ApplicationDocumentResponse storeDocument(Long applicationId, MultipartFile file, DocumentType documentType) {
        currentUserService.verifyNotDemo();
        User user = currentUserService.getCurrentUser();

        JobApplication application = jobApplicationRepository.findByIdAndUserId(applicationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Job Application", applicationId));

        // 1. Validate file (Size check, magic bytes check, zip bomb protection)
        FileValidator.validateFile(file);

        // 2. Safe Renaming & Sanitization
        String sanitizedOriginal = FileValidator.sanitizeFilename(file.getOriginalFilename());
        String storedFilename = documentType.name() + "_" + UUID.randomUUID().toString() + "_" + sanitizedOriginal;

        // 3. Check for replacement and record the old document to delete later
        Optional<ApplicationDocument> existing = Optional.empty();
        if (documentType == DocumentType.CV || documentType == DocumentType.COVER_LETTER) {
            existing = documentRepository.findByJobApplicationIdAndDocumentTypeAndJobApplicationUserId(applicationId, documentType, user.getId());
        }

        String storageRef = null;
        try {
            // Upload to storage first (Local or R2)
            storageRef = storageService.store(applicationId, file, documentType, storedFilename);
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to storage: " + sanitizedOriginal, e);
        }

        ApplicationDocument doc = null;
        try {
            // If replacing, delete the old database record first
            if (existing.isPresent()) {
                ApplicationDocument oldDoc = existing.get();
                documentRepository.delete(oldDoc);
                documentRepository.flush();
            }

            // Save the new record
            doc = ApplicationDocument.builder()
                    .jobApplication(application)
                    .fileName(sanitizedOriginal)
                    .fileType(file.getContentType())
                    .documentType(documentType)
                    .filePath(storageRef)
                    .uploadedAt(LocalDateTime.now())
                    .build();

            doc = documentRepository.save(doc);
            documentRepository.flush();

        } catch (Exception e) {
            // Rollback storage if database operation fails to avoid orphaned files
            if (storageRef != null) {
                try {
                    storageService.delete(storageRef);
                } catch (IOException ioException) {
                    log.error("Failed to clean up uploaded file in storage after DB failure: " + storageRef, ioException);
                }
            }
            throw new RuntimeException("Database error during document save: " + e.getMessage(), e);
        }

        // Delete the old file from storage since new DB record is successfully saved
        if (existing.isPresent()) {
            ApplicationDocument oldDoc = existing.get();
            try {
                storageService.delete(oldDoc.getFilePath());
            } catch (IOException e) {
                log.error("Failed to delete replaced document from storage: " + oldDoc.getFilePath(), e);
            }
        }

        return toResponse(doc);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDocumentResponse> getDocumentsByApplicationId(Long applicationId) {
        User user = currentUserService.getCurrentUser();

        if (!jobApplicationRepository.existsByIdAndUserId(applicationId, user.getId())) {
            throw new ResourceNotFoundException("Job Application", applicationId);
        }

        return documentRepository.findByJobApplicationIdAndJobApplicationUserIdOrderByUploadedAtDesc(applicationId, user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ApplicationDocument getDocument(Long documentId) {
        User user = currentUserService.getCurrentUser();
        return documentRepository.findByIdAndJobApplicationUserId(documentId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    @Transactional(readOnly = true)
    public InputStream loadDocumentAsStream(Long applicationId, Long documentId) {
        // getDocument already validates that document belongs to currently logged-in user
        ApplicationDocument doc = getDocument(documentId);
        if (!doc.getJobApplication().getId().equals(applicationId)) {
            throw new IllegalArgumentException("Document does not belong to the specified application");
        }

        try {
            return storageService.loadAsStream(doc.getFilePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file from storage for document: " + doc.getFileName(), e);
        }
    }

    @Transactional
    public void deleteDocument(Long applicationId, Long documentId) {
        currentUserService.verifyNotDemo();

        // getDocument already validates that document belongs to currently logged-in user
        ApplicationDocument doc = getDocument(documentId);
        if (!doc.getJobApplication().getId().equals(applicationId)) {
            throw new IllegalArgumentException("Document does not belong to the specified application");
        }

        // Delete from database first, then flush
        documentRepository.delete(doc);
        documentRepository.flush();

        // Delete from storage
        try {
            storageService.delete(doc.getFilePath());
        } catch (IOException e) {
            log.error("Failed to delete document from storage during deletion of document id: " + documentId, e);
        }
    }

    @Transactional
    public void deleteApplicationDocuments(Long applicationId) {
        currentUserService.verifyNotDemo();
        User user = currentUserService.getCurrentUser();

        List<ApplicationDocument> docs = documentRepository.findByJobApplicationIdAndJobApplicationUserIdOrderByUploadedAtDesc(applicationId, user.getId());

        // Delete records from database
        documentRepository.deleteAll(docs);
        documentRepository.flush();

        // Clean up from storage
        for (ApplicationDocument doc : docs) {
            try {
                storageService.delete(doc.getFilePath());
            } catch (IOException e) {
                log.error("Failed to delete document file from storage on application deletion: " + doc.getFilePath(), e);
            }
        }
    }

    public ApplicationDocumentResponse toResponse(ApplicationDocument doc) {
        return ApplicationDocumentResponse.builder()
                .id(doc.getId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .documentType(doc.getDocumentType())
                .uploadedAt(doc.getUploadedAt())
                .build();
    }
}
