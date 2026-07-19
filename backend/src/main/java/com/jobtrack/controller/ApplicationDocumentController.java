package com.jobtrack.controller;

import com.jobtrack.dto.ApplicationDocumentResponse;
import com.jobtrack.entity.ApplicationDocument;
import com.jobtrack.enums.DocumentType;
import com.jobtrack.service.ApplicationDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/applications/{applicationId}/documents")
@RequiredArgsConstructor
public class ApplicationDocumentController {

    private final ApplicationDocumentService documentService;

    @PostMapping
    public ResponseEntity<ApplicationDocumentResponse> uploadDocument(
            @PathVariable Long applicationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType) {
        
        ApplicationDocumentResponse response = documentService.storeDocument(applicationId, file, documentType);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ApplicationDocumentResponse>> getDocuments(@PathVariable Long applicationId) {
        return ResponseEntity.ok(documentService.getDocumentsByApplicationId(applicationId));
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<StreamingResponseBody> downloadDocument(
            @PathVariable Long applicationId,
            @PathVariable Long documentId) {
        
        ApplicationDocument doc = documentService.getDocument(documentId);
        if (!doc.getJobApplication().getId().equals(applicationId)) {
            throw new IllegalArgumentException("Document does not belong to the specified application");
        }

        InputStream inputStream = documentService.loadDocumentAsStream(applicationId, documentId);

        String contentType = doc.getFileType();
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream is = inputStream) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        };

        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(doc.getFileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(responseBody);
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long applicationId,
            @PathVariable Long documentId) {
        
        documentService.deleteDocument(applicationId, documentId);
        return ResponseEntity.noContent().build();
    }
}
