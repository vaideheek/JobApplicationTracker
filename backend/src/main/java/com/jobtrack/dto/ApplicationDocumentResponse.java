package com.jobtrack.dto;

import com.jobtrack.enums.DocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationDocumentResponse {
    private Long id;
    private String fileName;
    private String fileType;
    private DocumentType documentType;
    private LocalDateTime uploadedAt;
}
