package com.jobtrack.controller;

import com.jobtrack.entity.ApplicationDocument;
import com.jobtrack.enums.DocumentType;
import com.jobtrack.service.ApplicationDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/applications/{applicationId}/documents")
@RequiredArgsConstructor
public class ApplicationDocumentController {

    private final ApplicationDocumentService documentService;

    @PostMapping
    public ResponseEntity<ApplicationDocument> uploadDocument(
            @PathVariable Long applicationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType) {
        
        ApplicationDocument doc = documentService.storeDocument(applicationId, file, documentType);
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    @GetMapping
    public ResponseEntity<List<ApplicationDocument>> getDocuments(@PathVariable Long applicationId) {
        return ResponseEntity.ok(documentService.getDocumentsByApplicationId(applicationId));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable Long applicationId,
            @PathVariable Long documentId) {
        
        Resource resource = documentService.loadDocumentAsResource(applicationId, documentId);
        ApplicationDocument doc = documentService.getDocument(documentId);

        String contentType = doc.getFileType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getFileName() + "\"")
                .body(resource);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long applicationId,
            @PathVariable Long documentId) {
        
        documentService.deleteDocument(applicationId, documentId);
        return ResponseEntity.noContent().build();
    }
}
