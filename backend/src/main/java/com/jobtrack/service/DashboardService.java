package com.jobtrack.service;

import com.jobtrack.dto.DashboardInsightsResponse;
import com.jobtrack.dto.DashboardStats;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JobApplicationRepository jobApplicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    public DashboardStats getStats() {
        User user = currentUserService.getCurrentUser();
        Long userId = user.getId();

        long total = jobApplicationRepository.countByUserId(userId);
        long interviews = jobApplicationRepository.countByStatusAndUserId(ApplicationStatus.INTERVIEW, userId);
        long offers = jobApplicationRepository.countByStatusAndUserId(ApplicationStatus.OFFER, userId);
        long rejections = jobApplicationRepository.countByStatusAndUserId(ApplicationStatus.REJECTED, userId);

        // Calculate start of current week (Monday)
        LocalDate startOfWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long thisWeek = jobApplicationRepository.countByDateAppliedAfterAndUserId(startOfWeek.minusDays(1), userId);

        return DashboardStats.builder()
                .totalApplications(total)
                .interviews(interviews)
                .offers(offers)
                .rejections(rejections)
                .applicationsThisWeek(thisWeek)
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardInsightsResponse getInsights() {
        User user = currentUserService.getCurrentUser();
        Long userId = user.getId();

        LocalDate today = LocalDate.now();
        LocalDateTime fourteenDaysAgo = LocalDateTime.now().minusDays(14);
        LocalDate fourteenDaysFromNow = today.plusDays(14);

        long total = jobApplicationRepository.countByUserId(userId);

        // 1. New Dashboard Cards Counts
        long highPriority = jobApplicationRepository.countByPriorityAndUserId(ApplicationPriority.HIGH, userId);
        long followUpNeeded = jobApplicationRepository.countFollowUpNeeded(today, userId);
        long upcomingInterviews = jobApplicationRepository.countUpcomingInterviews(today, fourteenDaysFromNow, userId);
        long staleApps = jobApplicationRepository.countStaleApplications(fourteenDaysAgo, userId);
        long missingDocuments = jobApplicationRepository.countApplicationsMissingDocuments(userId);

        // 2. Conversion Rates
        double responseRate = 0.0;
        double interviewConversionRate = 0.0;
        double offerConversionRate = 0.0;

        if (total > 0) {
            // Response Rate = applications with status ASSESSMENT, INTERVIEW, OFFER, or REJECTED / total
            long responseCount = jobApplicationRepository.countByStatusAndUserId(ApplicationStatus.ASSESSMENT, userId)
                    + jobApplicationRepository.countByStatusAndUserId(ApplicationStatus.INTERVIEW, userId)
                    + jobApplicationRepository.countByStatusAndUserId(ApplicationStatus.OFFER, userId)
                    + jobApplicationRepository.countByStatusAndUserId(ApplicationStatus.REJECTED, userId);
            responseRate = ((double) responseCount / total) * 100.0;

            // Interview Conversion Rate = unique apps that reached INTERVIEW / total
            long reachedInterview = statusHistoryRepository.countDistinctApplicationsByToStatusAndUserId(ApplicationStatus.INTERVIEW, userId);
            reachedInterview = Math.min(reachedInterview, total);
            interviewConversionRate = ((double) reachedInterview / total) * 100.0;

            // Offer Conversion Rate = unique apps that reached OFFER / total
            long reachedOffer = statusHistoryRepository.countDistinctApplicationsByToStatusAndUserId(ApplicationStatus.OFFER, userId);
            reachedOffer = Math.min(reachedOffer, total);
            offerConversionRate = ((double) reachedOffer / total) * 100.0;
        }

        // 3. Top Companies
        List<Object[]> topCompaniesRaw = jobApplicationRepository.findTopCompanies(userId, PageRequest.of(0, 5));
        List<DashboardInsightsResponse.CompanyAppCount> topCompanies = topCompaniesRaw.stream()
                .map(row -> DashboardInsightsResponse.CompanyAppCount.builder()
                        .companyName((String) row[0])
                        .count((Long) row[1])
                        .build())
                .collect(Collectors.toList());

        // 4. Recommended Actions
        List<JobApplication> activeApps = jobApplicationRepository.findActiveApplications(userId);
        List<DashboardInsightsResponse.RecommendedAction> allActions = new ArrayList<>();

        for (JobApplication app : activeApps) {
            // Rule A: Overdue Follow-up
            if (app.getFollowUpDate() != null && !app.getFollowUpDate().isAfter(today)) {
                allActions.add(DashboardInsightsResponse.RecommendedAction.builder()
                        .type("FOLLOW_UP")
                        .applicationId(app.getId())
                        .companyName(app.getCompanyName())
                        .jobTitle(app.getJobTitle())
                        .message("Follow up on your application at " + app.getCompanyName())
                        .detail("Overdue since " + app.getFollowUpDate())
                        .build());
            }

            // Rule B: Prepare for Interview (followUpDate is interview/prep date within 7 days)
            if (app.getStatus() == ApplicationStatus.INTERVIEW && app.getFollowUpDate() != null 
                    && !app.getFollowUpDate().isBefore(today) && !app.getFollowUpDate().isAfter(today.plusDays(7))) {
                allActions.add(DashboardInsightsResponse.RecommendedAction.builder()
                        .type("INTERVIEW_PREP")
                        .applicationId(app.getId())
                        .companyName(app.getCompanyName())
                        .jobTitle(app.getJobTitle())
                        .message("Prepare for your " + (app.getStage() != null && !app.getStage().isBlank() ? app.getStage() : "interview") + " at " + app.getCompanyName())
                        .detail("Scheduled on " + app.getFollowUpDate())
                        .build());
            }

            // Rule C: Complete Assessment (deadlineDate or followUpDate is within 7 days)
            if (app.getStatus() == ApplicationStatus.ASSESSMENT) {
                LocalDate targetDate = app.getDeadlineDate() != null ? app.getDeadlineDate() : app.getFollowUpDate();
                if (targetDate != null && !targetDate.isBefore(today) && !targetDate.isAfter(today.plusDays(7))) {
                    allActions.add(DashboardInsightsResponse.RecommendedAction.builder()
                            .type("ASSESSMENT_COMPLETE")
                            .applicationId(app.getId())
                            .companyName(app.getCompanyName())
                            .jobTitle(app.getJobTitle())
                            .message("Complete the assessment for " + app.getCompanyName())
                            .detail("Due by " + targetDate)
                            .build());
                }
            }

            // Rule D: Review Stale Application (no update in 14+ days)
            if (app.getLastUpdatedAt().isBefore(fourteenDaysAgo)) {
                long daysStale = ChronoUnit.DAYS.between(app.getLastUpdatedAt().toLocalDate(), today);
                allActions.add(DashboardInsightsResponse.RecommendedAction.builder()
                        .type("REVIEW_STALE")
                        .applicationId(app.getId())
                        .companyName(app.getCompanyName())
                        .jobTitle(app.getJobTitle())
                        .message("Review your stale application at " + app.getCompanyName())
                        .detail("No updates in " + daysStale + " days")
                        .build());
            }
        }

        // Sorting & Limiting (Top 6)
        List<DashboardInsightsResponse.RecommendedAction> sortedActions = allActions.stream()
                .sorted((a, b) -> {
                    int typeOrderA = getActionTypeOrder(a.getType());
                    int typeOrderB = getActionTypeOrder(b.getType());
                    if (typeOrderA != typeOrderB) {
                        return Integer.compare(typeOrderA, typeOrderB);
                    }
                    
                    if ("FOLLOW_UP".equals(a.getType())) {
                        LocalDate dateA = getActionDate(activeApps, a.getApplicationId(), true);
                        LocalDate dateB = getActionDate(activeApps, b.getApplicationId(), true);
                        if (dateA != null && dateB != null) {
                            return dateA.compareTo(dateB); // Oldest first
                        }
                    } else if ("INTERVIEW_PREP".equals(a.getType()) || "ASSESSMENT_COMPLETE".equals(a.getType())) {
                        LocalDate dateA = getActionDate(activeApps, a.getApplicationId(), false);
                        LocalDate dateB = getActionDate(activeApps, b.getApplicationId(), false);
                        if (dateA != null && dateB != null) {
                            return dateA.compareTo(dateB); // Soonest first
                        }
                    } else if ("REVIEW_STALE".equals(a.getType())) {
                        LocalDateTime updateA = getActionUpdateTime(activeApps, a.getApplicationId());
                        LocalDateTime updateB = getActionUpdateTime(activeApps, b.getApplicationId());
                        if (updateA != null && updateB != null) {
                            return updateA.compareTo(updateB); // Oldest update first (longest stale)
                        }
                    }
                    return 0;
                })
                .limit(6)
                .collect(Collectors.toList());

        return DashboardInsightsResponse.builder()
                .highPriorityCount(highPriority)
                .followUpNeededCount(followUpNeeded)
                .upcomingInterviewsCount(upcomingInterviews)
                .staleApplicationsCount(staleApps)
                .missingDocumentsCount(missingDocuments)
                .topCompanies(topCompanies)
                .responseRate(responseRate)
                .interviewConversionRate(interviewConversionRate)
                .offerConversionRate(offerConversionRate)
                .recommendedActions(sortedActions)
                .build();
    }

    private int getActionTypeOrder(String type) {
        switch (type) {
            case "FOLLOW_UP": return 1;
            case "INTERVIEW_PREP":
            case "ASSESSMENT_COMPLETE": return 2;
            case "REVIEW_STALE": return 3;
            default: return 4;
        }
    }

    private LocalDate getActionDate(List<JobApplication> apps, Long id, boolean followUpOnly) {
        for (JobApplication app : apps) {
            if (app.getId().equals(id)) {
                if (followUpOnly) return app.getFollowUpDate();
                return app.getDeadlineDate() != null ? app.getDeadlineDate() : app.getFollowUpDate();
            }
        }
        return null;
    }

    private LocalDateTime getActionUpdateTime(List<JobApplication> apps, Long id) {
        for (JobApplication app : apps) {
            if (app.getId().equals(id)) {
                return app.getLastUpdatedAt();
            }
        }
        return null;
    }
}
