package com.jobtrack.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannedApplicationGroup {
    private String tempAppId;
    private String companyName;
    private String jobTitle;
    private String dateApplied;
    private String status;
    private String priority;
    private boolean isDuplicate;
    private Long existingApplicationId;
    private List<ScannedDocumentPreview> documents;
}
