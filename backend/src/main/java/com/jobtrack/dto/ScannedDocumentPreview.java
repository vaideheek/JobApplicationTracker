package com.jobtrack.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannedDocumentPreview {
    private String tempDocId;
    private String fileName;
    private String documentType;
    private long fileSize;
    private String sha256;
    private String validationStatus;
}
