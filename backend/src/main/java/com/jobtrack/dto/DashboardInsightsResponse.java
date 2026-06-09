package com.jobtrack.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardInsightsResponse {
    private long highPriorityCount;
    private long followUpNeededCount;
    private long upcomingInterviewsCount;
    private long staleApplicationsCount;

    private List<CompanyAppCount> topCompanies;
    private double responseRate;
    private double interviewConversionRate;
    private double offerConversionRate;

    private List<RecommendedAction> recommendedActions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CompanyAppCount {
        private String companyName;
        private long count;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendedAction {
        private String type; // "FOLLOW_UP", "INTERVIEW_PREP", "ASSESSMENT_COMPLETE", "REVIEW_STALE"
        private Long applicationId;
        private String companyName;
        private String jobTitle;
        private String message;
        private String detail;
    }
}
