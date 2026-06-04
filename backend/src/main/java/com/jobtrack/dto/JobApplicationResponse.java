package com.jobtrack.dto;

import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApplicationResponse {

    private Long id;
    private String companyName;
    private String jobTitle;
    private String location;
    private String jobUrl;
    private LocalDate dateApplied;
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
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdatedAt;
    private List<StatusHistoryResponse> statusHistory;
}
