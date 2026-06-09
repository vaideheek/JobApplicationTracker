package com.jobtrack.dto;

import com.jobtrack.enums.ApplicationStatus;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailParseResponse {
    private String companyName;
    private String jobTitle;
    private ApplicationStatus status;
    private String stage;
    private String recruiterEmail;
    private String source;
    private LocalDate importantDate;
    private String suggestedNotes;
    private String confidenceScore; // "HIGH", "MEDIUM", "LOW"
    private boolean needsReview;
}
