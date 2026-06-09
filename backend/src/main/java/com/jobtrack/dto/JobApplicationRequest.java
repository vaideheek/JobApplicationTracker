package com.jobtrack.dto;

import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplicationRequest {

    @NotBlank(message = "Company name is required")
    private String companyName;

    @NotBlank(message = "Job title is required")
    private String jobTitle;

    private String location;

    private String jobUrl;

    private LocalDate dateApplied;

    @NotNull(message = "Status is required")
    private ApplicationStatus status;

    private String stage;

    private String recruiterEmail;

    private String notes;

    private String source;

    private String companyCareerUrl;

    private String salaryRange;

    private ApplicationPriority priority;

    private LocalDate followUpDate;

    private LocalDate deadlineDate;

    private String jobDescription;

    private String jobDescriptionSummary;

    private String originalJobUrl;

    private Integer matchScore;

    private String matchedSkills;

    private String missingSkills;

}
