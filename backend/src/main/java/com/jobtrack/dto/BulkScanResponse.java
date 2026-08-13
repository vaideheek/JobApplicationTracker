package com.jobtrack.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkScanResponse {
    private String scanId;
    private List<ScannedApplicationGroup> applications;
    private List<ScannedDocumentPreview> unassignedFiles;
}
