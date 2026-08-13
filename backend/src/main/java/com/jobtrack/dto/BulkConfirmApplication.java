package com.jobtrack.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkConfirmApplication {
    private String tempAppId;
    private String companyName;
    private String jobTitle;
    private String status;
    private String priority;
    private String dateApplied;
    private String stage;
    private String source;
    private String notes;
    private List<BulkConfirmDocument> documents;
}
