package com.jobtrack.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodMetrics {
    private long applicationsSubmitted;
    private long responsesRecorded;
    private long assessmentsReached;
    private long interviewsReached;
    private long offersReached;
    private long rejectionsRecorded;
}
