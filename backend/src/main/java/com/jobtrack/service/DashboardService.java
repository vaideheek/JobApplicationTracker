package com.jobtrack.service;

import com.jobtrack.dto.*;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.enums.PeriodRange;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
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

    private static final List<ApplicationStatus> RESPONSE_STATUSES = List.of(
            ApplicationStatus.IN_REVIEW,
            ApplicationStatus.ASSESSMENT,
            ApplicationStatus.INTERVIEW,
            ApplicationStatus.OFFER,
            ApplicationStatus.REJECTED
    );

    private static final String HISTORY_METRIC_NOTE =
            "Status-event metrics use recorded non-import status history. Historical states imported without real transition dates are not assigned inferred event dates.";

    @Transactional(readOnly = true)
    public CurrentPipelineResponse getCurrentPipeline() {
        User user = currentUserService.getCurrentUser();
        Long userId = user.getId();

        List<Object[]> rows = jobApplicationRepository.countApplicationsByStatusGrouped(userId);
        long applied = 0;
        long inReview = 0;
        long assessment = 0;
        long interview = 0;
        long offer = 0;
        long noResponse = 0;
        long rejected = 0;
        long withdrawn = 0;

        for (Object[] row : rows) {
            ApplicationStatus status = (ApplicationStatus) row[0];
            long count = (Long) row[1];
            switch (status) {
                case APPLIED -> applied = count;
                case IN_REVIEW -> inReview = count;
                case ASSESSMENT -> assessment = count;
                case INTERVIEW -> interview = count;
                case OFFER -> offer = count;
                case NO_RESPONSE -> noResponse = count;
                case REJECTED -> rejected = count;
                case WITHDRAWN -> withdrawn = count;
            }
        }

        long totalActive = applied + inReview + assessment + interview + offer;

        return CurrentPipelineResponse.builder()
                .applied(applied)
                .inReview(inReview)
                .assessment(assessment)
                .interview(interview)
                .offer(offer)
                .noResponse(noResponse)
                .rejected(rejected)
                .withdrawn(withdrawn)
                .totalActive(totalActive)
                .build();
    }

    @Transactional(readOnly = true)
    public PeriodAnalyticsResponse getPeriodAnalytics(PeriodRange range, LocalDate customFrom, LocalDate customTo, boolean compare) {
        return getPeriodAnalytics(range, customFrom, customTo, compare, ZoneOffset.UTC);
    }

    @Transactional(readOnly = true)
    public PeriodAnalyticsResponse getPeriodAnalytics(PeriodRange range, LocalDate customFrom, LocalDate customTo, boolean compare, ZoneId zoneId) {
        User user = currentUserService.getCurrentUser();
        Long userId = user.getId();
        ZoneId effectiveZone = (zoneId != null) ? zoneId : ZoneOffset.UTC;
        LocalDate today = LocalDate.now(effectiveZone);

        if (range == null) {
            range = PeriodRange.THIS_MONTH;
        }

        // Validate custom vs preset dates
        if (range == PeriodRange.CUSTOM) {
            if (customFrom == null || customTo == null) {
                throw new IllegalArgumentException("Custom date range requires both 'from' and 'to' dates");
            }
            if (customFrom.isAfter(customTo)) {
                throw new IllegalArgumentException("Invalid date range: 'from' cannot be after 'to'");
            }
        } else {
            if (customFrom != null || customTo != null) {
                throw new IllegalArgumentException("Date parameters 'from' and 'to' are only permitted for CUSTOM range");
            }
        }

        ResolvedPeriod period = resolvePeriod(range, customFrom, customTo, today);

        LocalDate from = period.from();
        LocalDate to = period.to();
        LocalDate prevFrom = period.previousFrom();
        LocalDate prevTo = period.previousTo();

        PeriodMetrics currentMetrics;
        long unknownDateApps = 0;

        if (range == PeriodRange.ALL_TIME) {
            long totalApps = jobApplicationRepository.countByUserId(userId);
            unknownDateApps = jobApplicationRepository.countByUserIdAndDateAppliedIsNull(userId);
            long responses = statusHistoryRepository.countAllDistinctResponses(userId, RESPONSE_STATUSES);
            long assessments = statusHistoryRepository.countAllDistinctStatus(userId, ApplicationStatus.ASSESSMENT);
            long interviews = statusHistoryRepository.countAllDistinctStatus(userId, ApplicationStatus.INTERVIEW);
            long offers = statusHistoryRepository.countAllDistinctStatus(userId, ApplicationStatus.OFFER);
            long rejections = statusHistoryRepository.countAllDistinctStatus(userId, ApplicationStatus.REJECTED);

            currentMetrics = PeriodMetrics.builder()
                    .applicationsSubmitted(totalApps)
                    .responsesRecorded(responses)
                    .assessmentsReached(assessments)
                    .interviewsReached(interviews)
                    .offersReached(offers)
                    .rejectionsRecorded(rejections)
                    .build();
        } else {
            currentMetrics = calculateMetricsForRange(userId, from, to, effectiveZone);
        }

        PeriodMetrics previousMetrics = null;
        if (compare && range != PeriodRange.ALL_TIME && prevFrom != null && prevTo != null) {
            previousMetrics = calculateMetricsForRange(userId, prevFrom, prevTo, effectiveZone);
        }

        List<VolumeBucket> volume = calculateVolume(userId, range, from, to, today);

        return PeriodAnalyticsResponse.builder()
                .range(range)
                .from(from)
                .to(to)
                .previousFrom(previousMetrics != null ? prevFrom : null)
                .previousTo(previousMetrics != null ? prevTo : null)
                .unknownDateApplications(unknownDateApps)
                .current(currentMetrics)
                .previous(previousMetrics)
                .volume(volume)
                .historyMetricNote(HISTORY_METRIC_NOTE)
                .build();
    }

    private PeriodMetrics calculateMetricsForRange(Long userId, LocalDate from, LocalDate to) {
        return calculateMetricsForRange(userId, from, to, ZoneOffset.UTC);
    }

    private PeriodMetrics calculateMetricsForRange(Long userId, LocalDate from, LocalDate to, ZoneId zoneId) {
        long appsSubmitted = jobApplicationRepository.countApplicationsSubmittedInRange(userId, from, to);

        ZoneId effectiveZone = (zoneId != null) ? zoneId : ZoneOffset.UTC;
        LocalDateTime startUtc = from.atStartOfDay(effectiveZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
        LocalDateTime endExclusiveUtc = to.plusDays(1).atStartOfDay(effectiveZone)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();

        long responses = statusHistoryRepository.countDistinctResponsesInRange(userId, RESPONSE_STATUSES, startUtc, endExclusiveUtc);
        long assessments = statusHistoryRepository.countDistinctStatusInRange(userId, ApplicationStatus.ASSESSMENT, startUtc, endExclusiveUtc);
        long interviews = statusHistoryRepository.countDistinctStatusInRange(userId, ApplicationStatus.INTERVIEW, startUtc, endExclusiveUtc);
        long offers = statusHistoryRepository.countDistinctStatusInRange(userId, ApplicationStatus.OFFER, startUtc, endExclusiveUtc);
        long rejections = statusHistoryRepository.countDistinctStatusInRange(userId, ApplicationStatus.REJECTED, startUtc, endExclusiveUtc);

        return PeriodMetrics.builder()
                .applicationsSubmitted(appsSubmitted)
                .responsesRecorded(responses)
                .assessmentsReached(assessments)
                .interviewsReached(interviews)
                .offersReached(offers)
                .rejectionsRecorded(rejections)
                .build();
    }

    public record ResolvedPeriod(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo) {}

    public ResolvedPeriod resolvePeriod(PeriodRange range, LocalDate customFrom, LocalDate customTo, LocalDate today) {
        switch (range) {
            case THIS_WEEK -> {
                LocalDate from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate to = today;
                LocalDate prevFrom = from.minusWeeks(1);
                LocalDate prevTo = to.minusWeeks(1);
                return new ResolvedPeriod(from, to, prevFrom, prevTo);
            }
            case LAST_WEEK -> {
                LocalDate from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
                LocalDate to = from.plusDays(6);
                LocalDate prevFrom = from.minusWeeks(1);
                LocalDate prevTo = to.minusWeeks(1);
                return new ResolvedPeriod(from, to, prevFrom, prevTo);
            }
            case THIS_MONTH -> {
                LocalDate from = today.withDayOfMonth(1);
                LocalDate to = today;
                LocalDate prevMonth = today.minusMonths(1);
                LocalDate prevFrom = prevMonth.withDayOfMonth(1);
                int targetDay = today.getDayOfMonth();
                int maxPrevDay = prevMonth.lengthOfMonth();
                LocalDate prevTo = prevMonth.withDayOfMonth(Math.min(targetDay, maxPrevDay));
                return new ResolvedPeriod(from, to, prevFrom, prevTo);
            }
            case LAST_MONTH -> {
                LocalDate prevMonth = today.minusMonths(1);
                LocalDate from = prevMonth.withDayOfMonth(1);
                LocalDate to = prevMonth.withDayOfMonth(prevMonth.lengthOfMonth());
                LocalDate monthBefore = today.minusMonths(2);
                LocalDate prevFrom = monthBefore.withDayOfMonth(1);
                LocalDate prevTo = monthBefore.withDayOfMonth(monthBefore.lengthOfMonth());
                return new ResolvedPeriod(from, to, prevFrom, prevTo);
            }
            case LAST_30_DAYS -> {
                LocalDate from = today.minusDays(29);
                LocalDate to = today;
                LocalDate prevFrom = from.minusDays(30);
                LocalDate prevTo = from.minusDays(1);
                return new ResolvedPeriod(from, to, prevFrom, prevTo);
            }
            case LAST_90_DAYS -> {
                LocalDate from = today.minusDays(89);
                LocalDate to = today;
                LocalDate prevFrom = from.minusDays(90);
                LocalDate prevTo = from.minusDays(1);
                return new ResolvedPeriod(from, to, prevFrom, prevTo);
            }
            case YTD -> {
                LocalDate from = LocalDate.of(today.getYear(), 1, 1);
                LocalDate to = today;
                LocalDate prevFrom = LocalDate.of(today.getYear() - 1, 1, 1);
                LocalDate prevTo;
                if (today.getMonthValue() == 2 && today.getDayOfMonth() == 29) {
                    prevTo = LocalDate.of(today.getYear() - 1, 2, 28);
                } else {
                    prevTo = today.minusYears(1);
                }
                return new ResolvedPeriod(from, to, prevFrom, prevTo);
            }
            case CUSTOM -> {
                long days = ChronoUnit.DAYS.between(customFrom, customTo) + 1;
                LocalDate prevTo = customFrom.minusDays(1);
                LocalDate prevFrom = customFrom.minusDays(days);
                return new ResolvedPeriod(customFrom, customTo, prevFrom, prevTo);
            }
            case ALL_TIME -> {
                return new ResolvedPeriod(null, null, null, null);
            }
            default -> throw new IllegalArgumentException("Unsupported period range: " + range);
        }
    }

    private List<VolumeBucket> calculateVolume(Long userId, PeriodRange range, LocalDate from, LocalDate to, LocalDate today) {
        DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("MMM d");
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy");

        if (range == PeriodRange.ALL_TIME) {
            List<Object[]> rawCounts = jobApplicationRepository.findAllDailyApplicationCounts(userId);
            if (rawCounts.isEmpty()) {
                YearMonth cur = YearMonth.from(today);
                return List.of(VolumeBucket.builder()
                        .bucketStart(cur.atDay(1).toString())
                        .label(cur.format(monthFormatter))
                        .applicationsSubmitted(0L)
                        .build());
            }

            Map<LocalDate, Long> countMap = new HashMap<>();
            LocalDate minDate = (LocalDate) rawCounts.get(0)[0];
            LocalDate maxDate = (LocalDate) rawCounts.get(0)[0];

            for (Object[] row : rawCounts) {
                LocalDate date = (LocalDate) row[0];
                long count = (Long) row[1];
                countMap.put(date, count);
                if (date.isBefore(minDate)) minDate = date;
                if (date.isAfter(maxDate)) maxDate = date;
            }

            if (maxDate.isBefore(today)) {
                maxDate = today;
            }

            YearMonth startYm = YearMonth.from(minDate);
            YearMonth endYm = YearMonth.from(maxDate);

            List<VolumeBucket> buckets = new ArrayList<>();
            YearMonth cur = startYm;
            while (!cur.isAfter(endYm)) {
                long sum = 0;
                LocalDate curStart = cur.atDay(1);
                LocalDate curEnd = cur.atEndOfMonth();
                for (Map.Entry<LocalDate, Long> entry : countMap.entrySet()) {
                    if (!entry.getKey().isBefore(curStart) && !entry.getKey().isAfter(curEnd)) {
                        sum += entry.getValue();
                    }
                }
                buckets.add(VolumeBucket.builder()
                        .bucketStart(curStart.toString())
                        .label(cur.format(monthFormatter))
                        .applicationsSubmitted(sum)
                        .build());
                cur = cur.plusMonths(1);
            }
            return buckets;
        }

        // Dated period
        List<Object[]> rawCounts = jobApplicationRepository.findDailyApplicationCountsInRange(userId, from, to);
        Map<LocalDate, Long> countMap = new HashMap<>();
        for (Object[] row : rawCounts) {
            countMap.put((LocalDate) row[0], (Long) row[1]);
        }

        long totalDays = ChronoUnit.DAYS.between(from, to) + 1;
        List<VolumeBucket> buckets = new ArrayList<>();

        if (totalDays <= 31) {
            // Daily buckets
            LocalDate d = from;
            while (!d.isAfter(to)) {
                buckets.add(VolumeBucket.builder()
                        .bucketStart(d.toString())
                        .label(d.format(dayFormatter))
                        .applicationsSubmitted(countMap.getOrDefault(d, 0L))
                        .build());
                d = d.plusDays(1);
            }
        } else if (totalDays <= 180) {
            // Weekly buckets (7-day slices)
            LocalDate curStart = from;
            while (!curStart.isAfter(to)) {
                LocalDate curEnd = curStart.plusDays(6);
                if (curEnd.isAfter(to)) {
                    curEnd = to;
                }
                long sum = 0;
                LocalDate d = curStart;
                while (!d.isAfter(curEnd)) {
                    sum += countMap.getOrDefault(d, 0L);
                    d = d.plusDays(1);
                }
                String label = curStart.format(dayFormatter) + " - " + curEnd.format(dayFormatter);
                buckets.add(VolumeBucket.builder()
                        .bucketStart(curStart.toString())
                        .label(label)
                        .applicationsSubmitted(sum)
                        .build());
                curStart = curEnd.plusDays(1);
            }
        } else {
            // Monthly buckets
            YearMonth startYm = YearMonth.from(from);
            YearMonth endYm = YearMonth.from(to);
            YearMonth cur = startYm;
            while (!cur.isAfter(endYm)) {
                LocalDate curStart = cur.atDay(1);
                if (curStart.isBefore(from)) curStart = from;
                LocalDate curEnd = cur.atEndOfMonth();
                if (curEnd.isAfter(to)) curEnd = to;

                long sum = 0;
                LocalDate d = curStart;
                while (!d.isAfter(curEnd)) {
                    sum += countMap.getOrDefault(d, 0L);
                    d = d.plusDays(1);
                }

                buckets.add(VolumeBucket.builder()
                        .bucketStart(cur.atDay(1).toString())
                        .label(cur.format(monthFormatter))
                        .applicationsSubmitted(sum)
                        .build());
                cur = cur.plusMonths(1);
            }
        }

        return buckets;
    }
}
