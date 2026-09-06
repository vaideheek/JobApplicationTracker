package com.jobtrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.StatusHistory;
import com.jobtrack.entity.User;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.repository.ApplicationDocumentRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusHistoryRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.enums.PeriodRange;
import com.jobtrack.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DashboardPeriodAnalyticsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private JobApplicationRepository applicationRepository;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @Autowired
    private ApplicationDocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        statusHistoryRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();

        // Seed primary user "owner"
        testUser = User.builder()
                .username("owner")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("Owner User")
                .role("ROLE_USER")
                .enabled(true)
                .demoAccount(false)
                .createdAt(LocalDateTime.now())
                .build();
        testUser = userRepository.save(testUser);

        // Seed secondary user "other" for isolation testing
        otherUser = User.builder()
                .username("other")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("Other User")
                .role("ROLE_USER")
                .enabled(true)
                .demoAccount(false)
                .createdAt(LocalDateTime.now())
                .build();
        otherUser = userRepository.save(otherUser);
    }

    @Test
    @WithMockUser(username = "owner")
    void test1_ApplicationsSubmittedBoundariesInclusive() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        // Exactly at 'from' boundary
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(from).build());
        // In the middle
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 5)).build());
        // Exactly at 'to' boundary
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(to).build());
        // Before boundary
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("D").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(from.minusDays(1)).build());
        // After boundary
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("E").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(to.plusDays(1)).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(3));
    }

    @Test
    @WithMockUser(username = "owner")
    void test2_NullDateAppliedExcludedFromDatedPeriod() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(null).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(from).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(1))
                .andExpect(jsonPath("$.unknownDateApplications").value(0));
    }

    @Test
    @WithMockUser(username = "owner")
    void test3_UserOwnershipIsolationForApplicationCounts() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        applicationRepository.save(JobApplication.builder().user(testUser).companyName("Owner Corp").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 3)).build());
        applicationRepository.save(JobApplication.builder().user(otherUser).companyName("Other Corp").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 3)).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test4_UserOwnershipIsolationForStatusHistoryMetrics() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        JobApplication ownerApp = applicationRepository.save(JobApplication.builder().user(testUser).companyName("Owner Corp").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        JobApplication otherApp = applicationRepository.save(JobApplication.builder().user(otherUser).companyName("Other Corp").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());

        statusHistoryRepository.save(StatusHistory.builder().jobApplication(ownerApp).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 3, 10, 0)).note("Interview invite").build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(otherApp).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 3, 10, 0)).note("Interview invite").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.interviewsReached").value(1))
                .andExpect(jsonPath("$.current.responsesRecorded").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test5to12_ResponseStateTransitions() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        // 5: IN_REVIEW counts as response
        JobApplication appInReview = applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.IN_REVIEW).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(appInReview).toStatus(ApplicationStatus.IN_REVIEW).changedAt(LocalDateTime.of(2026, 8, 2, 9, 0)).note("Under review").build());

        // 6: ASSESSMENT counts as response
        JobApplication appAssessment = applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.ASSESSMENT).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(appAssessment).toStatus(ApplicationStatus.ASSESSMENT).changedAt(LocalDateTime.of(2026, 8, 3, 9, 0)).note("Assessment sent").build());

        // 7: INTERVIEW counts as response
        JobApplication appInterview = applicationRepository.save(JobApplication.builder().user(testUser).companyName("C").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(appInterview).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 4, 9, 0)).note("Interview schedule").build());

        // 8: OFFER counts as response
        JobApplication appOffer = applicationRepository.save(JobApplication.builder().user(testUser).companyName("D").jobTitle("Dev").status(ApplicationStatus.OFFER).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(appOffer).toStatus(ApplicationStatus.OFFER).changedAt(LocalDateTime.of(2026, 8, 5, 9, 0)).note("Offer extended").build());

        // 9: REJECTED counts as response
        JobApplication appRejected = applicationRepository.save(JobApplication.builder().user(testUser).companyName("E").jobTitle("Dev").status(ApplicationStatus.REJECTED).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(appRejected).toStatus(ApplicationStatus.REJECTED).changedAt(LocalDateTime.of(2026, 8, 6, 9, 0)).note("Rejection email").build());

        // 10: NO_RESPONSE does not count as response
        JobApplication appNoResponse = applicationRepository.save(JobApplication.builder().user(testUser).companyName("F").jobTitle("Dev").status(ApplicationStatus.NO_RESPONSE).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(appNoResponse).toStatus(ApplicationStatus.NO_RESPONSE).changedAt(LocalDateTime.of(2026, 8, 7, 9, 0)).note("No response").build());

        // 11: WITHDRAWN does not count as response
        JobApplication appWithdrawn = applicationRepository.save(JobApplication.builder().user(testUser).companyName("G").jobTitle("Dev").status(ApplicationStatus.WITHDRAWN).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(appWithdrawn).toStatus(ApplicationStatus.WITHDRAWN).changedAt(LocalDateTime.of(2026, 8, 8, 9, 0)).note("Candidate withdrew").build());

        // 12: APPLIED does not count as response
        JobApplication appApplied = applicationRepository.save(JobApplication.builder().user(testUser).companyName("H").jobTitle("Dev").status(ApplicationStatus.APPLIED).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(appApplied).toStatus(ApplicationStatus.APPLIED).changedAt(LocalDateTime.of(2026, 8, 1, 9, 0)).note("Application submitted").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(5))
                .andExpect(jsonPath("$.current.assessmentsReached").value(1))
                .andExpect(jsonPath("$.current.interviewsReached").value(1))
                .andExpect(jsonPath("$.current.offersReached").value(1))
                .andExpect(jsonPath("$.current.rejectionsRecorded").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test13_SameApplicationWithMultipleResponsesCountsOnce() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        JobApplication app = applicationRepository.save(JobApplication.builder().user(testUser).companyName("Acme").jobTitle("Dev").status(ApplicationStatus.OFFER).build());

        // Application progressed from IN_REVIEW -> ASSESSMENT -> INTERVIEW -> OFFER all within the period
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.IN_REVIEW).changedAt(LocalDateTime.of(2026, 8, 2, 9, 0)).note("Review").build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.ASSESSMENT).changedAt(LocalDateTime.of(2026, 8, 4, 9, 0)).note("Assessment").build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 6, 9, 0)).note("Interview").build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.OFFER).changedAt(LocalDateTime.of(2026, 8, 8, 9, 0)).note("Offer").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(1))
                .andExpect(jsonPath("$.current.assessmentsReached").value(1))
                .andExpect(jsonPath("$.current.interviewsReached").value(1))
                .andExpect(jsonPath("$.current.offersReached").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test14to17_DistinctReachedMetricCounts() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        JobApplication app1 = applicationRepository.save(JobApplication.builder().user(testUser).companyName("A1").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        JobApplication app2 = applicationRepository.save(JobApplication.builder().user(testUser).companyName("A2").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());

        // Multiple interview rounds for app1
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app1).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 2, 9, 0)).note("Round 1").build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app1).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 4, 9, 0)).note("Round 2").build());
        // One round for app2
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app2).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 5, 9, 0)).note("Round 1").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.interviewsReached").value(2));
    }

    @Test
    @WithMockUser(username = "owner")
    void test18and19_StartBoundaryInclusiveEndExclusive() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        JobApplication app1 = applicationRepository.save(JobApplication.builder().user(testUser).companyName("A1").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        JobApplication app2 = applicationRepository.save(JobApplication.builder().user(testUser).companyName("A2").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        JobApplication app3 = applicationRepository.save(JobApplication.builder().user(testUser).companyName("A3").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        JobApplication app4 = applicationRepository.save(JobApplication.builder().user(testUser).companyName("A4").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());

        // Exact start boundary: 2026-08-01 00:00:00 -> INCLUDED (test 18)
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app1).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 1, 0, 0, 0)).note("Exact start").build());

        // Inside range: 2026-08-10 23:59:59 -> INCLUDED
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app2).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 10, 23, 59, 59)).note("Inside range").build());

        // Exact endExclusive boundary: 2026-08-11 00:00:00 -> EXCLUDED (test 19)
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app3).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 11, 0, 0, 0)).note("End boundary").build());

        // Before start: 2026-07-31 23:59:59 -> EXCLUDED
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app4).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 7, 31, 23, 59, 59)).note("Before start").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.interviewsReached").value(2));
    }

    @Test
    @WithMockUser(username = "owner")
    void test20_PreviousPeriodCalculation() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 11);
        LocalDate to = LocalDate.of(2026, 8, 20); // 10 days
        // Preceding equal 10-day period is 2026-08-01 to 2026-08-10

        // In previous period
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("Prev Corp").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 5)).build());
        // In current period
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("Cur Corp").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 15)).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("compare", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-08-11"))
                .andExpect(jsonPath("$.to").value("2026-08-20"))
                .andExpect(jsonPath("$.previousFrom").value("2026-08-01"))
                .andExpect(jsonPath("$.previousTo").value("2026-08-10"))
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(1))
                .andExpect(jsonPath("$.previous.applicationsSubmitted").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test21_AuthenticatedUserCannotSeeAnotherUserMetrics() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        JobApplication otherApp = applicationRepository.save(JobApplication.builder().user(otherUser).companyName("Other Corp").jobTitle("Dev").status(ApplicationStatus.OFFER).dateApplied(LocalDate.of(2026, 8, 5)).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(otherApp).toStatus(ApplicationStatus.OFFER).changedAt(LocalDateTime.of(2026, 8, 5, 10, 0)).note("Offer").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(0))
                .andExpect(jsonPath("$.current.offersReached").value(0));
    }

    @Test
    @WithMockUser(username = "owner")
    void test22_VolumeBucketsTotalEqualsSubmittedApplications() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10); // 10 days -> daily buckets

        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 1)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 1)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 5)).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(3))
                .andExpect(jsonPath("$.volume", hasSize(10)))
                .andExpect(jsonPath("$.volume[0].applicationsSubmitted").value(2))
                .andExpect(jsonPath("$.volume[4].applicationsSubmitted").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test23_EmptyPeriodReturnsZerosCleanly() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(0))
                .andExpect(jsonPath("$.current.responsesRecorded").value(0))
                .andExpect(jsonPath("$.current.assessmentsReached").value(0))
                .andExpect(jsonPath("$.current.interviewsReached").value(0))
                .andExpect(jsonPath("$.current.offersReached").value(0))
                .andExpect(jsonPath("$.current.rejectionsRecorded").value(0))
                .andExpect(jsonPath("$.volume", hasSize(10)));
    }

    @Test
    @WithMockUser(username = "owner")
    void test24to26_SyntheticBulkImportHistoryExclusion() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);

        JobApplication appBulkNew = applicationRepository.save(JobApplication.builder().user(testUser).companyName("Bulk New").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        JobApplication appBulkUpdate = applicationRepository.save(JobApplication.builder().user(testUser).companyName("Bulk Update").jobTitle("Dev").status(ApplicationStatus.REJECTED).build());
        JobApplication appNormal = applicationRepository.save(JobApplication.builder().user(testUser).companyName("Normal").jobTitle("Dev").status(ApplicationStatus.OFFER).build());

        // 24: "Bulk imported application" note -> EXCLUDED
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(appBulkNew)
                .toStatus(ApplicationStatus.INTERVIEW)
                .changedAt(LocalDateTime.of(2026, 8, 5, 12, 0))
                .note("Bulk imported application")
                .build());

        // 25: note containing "via bulk import" -> EXCLUDED
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(appBulkUpdate)
                .toStatus(ApplicationStatus.REJECTED)
                .changedAt(LocalDateTime.of(2026, 8, 5, 12, 0))
                .note("Status changed from APPLIED to REJECTED via bulk import")
                .build());

        // 26: Normal recorded history -> INCLUDED
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(appNormal)
                .toStatus(ApplicationStatus.OFFER)
                .changedAt(LocalDateTime.of(2026, 8, 5, 12, 0))
                .note("Status changed from INTERVIEW to OFFER")
                .build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.interviewsReached").value(0)) // synthetic bulk import excluded!
                .andExpect(jsonPath("$.current.rejectionsRecorded").value(0)) // synthetic bulk import update excluded!
                .andExpect(jsonPath("$.current.offersReached").value(1)) // normal history included!
                .andExpect(jsonPath("$.current.responsesRecorded").value(1)); // only normal counted
    }

    @Test
    @WithMockUser(username = "owner")
    void test27_PresetResolutionThisWeek() throws Exception {
        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "THIS_WEEK")
                        .param("compare", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("THIS_WEEK"))
                .andExpect(jsonPath("$.from").isNotEmpty())
                .andExpect(jsonPath("$.to").isNotEmpty())
                .andExpect(jsonPath("$.previousFrom").isNotEmpty())
                .andExpect(jsonPath("$.previousTo").isNotEmpty())
                .andExpect(jsonPath("$.previous").isMap());
    }

    @Test
    @WithMockUser(username = "owner")
    void test28_PresetResolutionThisMonth() throws Exception {
        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "THIS_MONTH")
                        .param("compare", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("THIS_MONTH"))
                .andExpect(jsonPath("$.from").isNotEmpty())
                .andExpect(jsonPath("$.to").isNotEmpty())
                .andExpect(jsonPath("$.previousFrom").isNotEmpty())
                .andExpect(jsonPath("$.previousTo").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "owner")
    void test29_DateValidationAndContradictoryRejection() throws Exception {
        // Missing from/to on CUSTOM
        mockMvc.perform(get("/api/dashboard/period").param("range", "CUSTOM"))
                .andExpect(status().isBadRequest());

        // from > to on CUSTOM
        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-08-10")
                        .param("to", "2026-08-01"))
                .andExpect(status().isBadRequest());

        // Non-CUSTOM with explicit from/to dates rejected
        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "THIS_MONTH")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-10"))
                .andExpect(status().isBadRequest());

        // Unsupported range
        mockMvc.perform(get("/api/dashboard/period").param("range", "INVALID_RANGE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "owner")
    void test30_AllTimeIncludesNullDateAppliedAndReportsUnknownDateApplications() throws Exception {
        // App with date
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("Dated Corp").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 1)).build());
        // App without date
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("Undated Corp").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(null).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "ALL_TIME")
                        .param("compare", "true")) // compare is ignored for ALL_TIME
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("ALL_TIME"))
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(2)) // Includes undated!
                .andExpect(jsonPath("$.unknownDateApplications").value(1))
                .andExpect(jsonPath("$.previous").doesNotExist()) // Comparison disabled for ALL_TIME
                .andExpect(jsonPath("$.volume[0].applicationsSubmitted").value(1)); // Chart only buckets dated
    }

    @Test
    @WithMockUser(username = "owner")
    void test31_CurrentPipelineGroupedCountsAndTotalActive() throws Exception {
        // 1 Applied, 2 In Review, 1 Assessment, 1 Interview, 1 Offer -> Total Active = 6
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C1").jobTitle("Dev").status(ApplicationStatus.APPLIED).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C2").jobTitle("Dev").status(ApplicationStatus.IN_REVIEW).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C3").jobTitle("Dev").status(ApplicationStatus.IN_REVIEW).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C4").jobTitle("Dev").status(ApplicationStatus.ASSESSMENT).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C5").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C6").jobTitle("Dev").status(ApplicationStatus.OFFER).build());

        // Inactive / closed outcomes -> NOT in totalActive
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C7").jobTitle("Dev").status(ApplicationStatus.NO_RESPONSE).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C8").jobTitle("Dev").status(ApplicationStatus.REJECTED).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("C9").jobTitle("Dev").status(ApplicationStatus.WITHDRAWN).build());

        // Other user's application (isolation test)
        applicationRepository.save(JobApplication.builder().user(otherUser).companyName("C10").jobTitle("Dev").status(ApplicationStatus.OFFER).build());

        mockMvc.perform(get("/api/dashboard/pipeline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(1))
                .andExpect(jsonPath("$.inReview").value(2))
                .andExpect(jsonPath("$.assessment").value(1))
                .andExpect(jsonPath("$.interview").value(1))
                .andExpect(jsonPath("$.offer").value(1))
                .andExpect(jsonPath("$.noResponse").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.withdrawn").value(1))
                .andExpect(jsonPath("$.totalActive").value(6)); // strictly 1+2+1+1+1
    }

    // ==========================================
    // Tests 32 to 40: Explicit Response Semantics
    // ==========================================

    @Test
    @WithMockUser(username = "owner")
    void test32_InReviewCountsAsResponse() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);
        JobApplication app = applicationRepository.save(JobApplication.builder().user(testUser).companyName("AppInReview").jobTitle("Dev").status(ApplicationStatus.IN_REVIEW).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.IN_REVIEW).changedAt(LocalDateTime.of(2026, 8, 3, 10, 0)).note("Recruiter reviewing").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test33_AssessmentCountsAsResponse() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);
        JobApplication app = applicationRepository.save(JobApplication.builder().user(testUser).companyName("AppAssessment").jobTitle("Dev").status(ApplicationStatus.ASSESSMENT).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.ASSESSMENT).changedAt(LocalDateTime.of(2026, 8, 3, 10, 0)).note("Take-home challenge").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(1))
                .andExpect(jsonPath("$.current.assessmentsReached").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test34_InterviewCountsAsResponse() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);
        JobApplication app = applicationRepository.save(JobApplication.builder().user(testUser).companyName("AppInterview").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 3, 10, 0)).note("Round 1 interview").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(1))
                .andExpect(jsonPath("$.current.interviewsReached").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test35_OfferCountsAsResponse() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);
        JobApplication app = applicationRepository.save(JobApplication.builder().user(testUser).companyName("AppOffer").jobTitle("Dev").status(ApplicationStatus.OFFER).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.OFFER).changedAt(LocalDateTime.of(2026, 8, 3, 10, 0)).note("Official offer letter").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(1))
                .andExpect(jsonPath("$.current.offersReached").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test36_RejectedCountsAsResponse() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);
        JobApplication app = applicationRepository.save(JobApplication.builder().user(testUser).companyName("AppRejected").jobTitle("Dev").status(ApplicationStatus.REJECTED).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.REJECTED).changedAt(LocalDateTime.of(2026, 8, 3, 10, 0)).note("Company decided to move on").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(1))
                .andExpect(jsonPath("$.current.rejectionsRecorded").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test37_NoResponseDoesNotCountAsResponse() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);
        JobApplication app = applicationRepository.save(JobApplication.builder().user(testUser).companyName("AppNoResponse").jobTitle("Dev").status(ApplicationStatus.NO_RESPONSE).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.NO_RESPONSE).changedAt(LocalDateTime.of(2026, 8, 3, 10, 0)).note("Ghosted").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(0));
    }

    @Test
    @WithMockUser(username = "owner")
    void test38_WithdrawnDoesNotCountAsResponse() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);
        JobApplication app = applicationRepository.save(JobApplication.builder().user(testUser).companyName("AppWithdrawn").jobTitle("Dev").status(ApplicationStatus.WITHDRAWN).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.WITHDRAWN).changedAt(LocalDateTime.of(2026, 8, 3, 10, 0)).note("Accepted another role").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(0));
    }

    @Test
    @WithMockUser(username = "owner")
    void test39_AppliedDoesNotCountAsResponse() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);
        JobApplication app = applicationRepository.save(JobApplication.builder().user(testUser).companyName("AppApplied").jobTitle("Dev").status(ApplicationStatus.APPLIED).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.APPLIED).changedAt(LocalDateTime.of(2026, 8, 1, 10, 0)).note("Initial application").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(0));
    }

    @Test
    @WithMockUser(username = "owner")
    void test40_MultipleEligibleTransitionsForSameAppCountOnce() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 10);
        JobApplication app = applicationRepository.save(JobApplication.builder().user(testUser).companyName("MultiApp").jobTitle("Dev").status(ApplicationStatus.OFFER).build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.IN_REVIEW).changedAt(LocalDateTime.of(2026, 8, 2, 10, 0)).note("In review").build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.ASSESSMENT).changedAt(LocalDateTime.of(2026, 8, 4, 10, 0)).note("Assessment").build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.INTERVIEW).changedAt(LocalDateTime.of(2026, 8, 6, 10, 0)).note("Interview").build());
        statusHistoryRepository.save(StatusHistory.builder().jobApplication(app).toStatus(ApplicationStatus.OFFER).changedAt(LocalDateTime.of(2026, 8, 8, 10, 0)).note("Offer").build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.responsesRecorded").value(1))
                .andExpect(jsonPath("$.current.assessmentsReached").value(1))
                .andExpect(jsonPath("$.current.interviewsReached").value(1))
                .andExpect(jsonPath("$.current.offersReached").value(1));
    }

    // ==========================================
    // Tests 41 to 45: Volume Bucket Granularity Boundaries
    // ==========================================

    @Test
    @WithMockUser(username = "owner")
    void test41_VolumeBucketBoundary_31Days_Daily() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 8, 31); // 31 days

        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A1").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 5)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A2").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 20)).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volume.length()").value(31))
                .andExpect(jsonPath("$.volume[0].label").value("Aug 1"))
                .andExpect(jsonPath("$.volume[30].label").value("Aug 31"))
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(2));
    }

    @Test
    @WithMockUser(username = "owner")
    void test42_VolumeBucketBoundary_32Days_Weekly() throws Exception {
        LocalDate from = LocalDate.of(2026, 8, 1);
        LocalDate to = LocalDate.of(2026, 9, 1); // 32 days

        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A1").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 1)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A2").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 15)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A3").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 9, 1)).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volume[0].label").value("Aug 1 - Aug 7"))
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(3));
    }

    @Test
    @WithMockUser(username = "owner")
    void test43_VolumeBucketBoundary_180Days_Weekly() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 6, 29); // 180 days in 2026 non-leap year

        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A1").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 1, 15)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A2").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 6, 15)).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volume[0].label").value("Jan 1 - Jan 7"))
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(2));
    }

    @Test
    @WithMockUser(username = "owner")
    void test44_VolumeBucketBoundary_181Days_Monthly() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 6, 30); // 181 days

        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A1").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 1, 15)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A2").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 6, 15)).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.volume.length()").value(6))
                .andExpect(jsonPath("$.volume[0].label").value("Jan 2026"))
                .andExpect(jsonPath("$.volume[5].label").value("Jun 2026"))
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(2));
    }

    @Test
    @WithMockUser(username = "owner")
    void test45_VolumeBucketBoundary_AllTime_Monthly() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A1").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 1, 10)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A2").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 10)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A3").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(null).build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "ALL_TIME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("ALL_TIME"))
                .andExpect(jsonPath("$.volume[0].label").value(containsString("2026")))
                .andExpect(jsonPath("$.current.applicationsSubmitted").value(3))
                .andExpect(jsonPath("$.unknownDateApplications").value(1));
    }

    // ==========================================
    // Tests 46 to 48: Timezone Handling
    // ==========================================

    @Test
    @WithMockUser(username = "owner")
    void test46_ValidTimezoneAccepted() throws Exception {
        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "THIS_WEEK")
                        .param("timezone", "Europe/Budapest"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "owner")
    void test47_InvalidTimezoneRejected() throws Exception {
        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "THIS_WEEK")
                        .param("timezone", "Invalid/Zone_FooBar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Invalid timezone")));
    }

    @Test
    @WithMockUser(username = "owner")
    void test48_TimezoneDateBoundaryPresetResolution() throws Exception {
        // Pacific/Kiritimati is UTC+14, Pacific/Niue is UTC-11.
        // The 25-hour offset difference guarantees they are always on different calendar dates.
        LocalDate dateKiritimati = LocalDate.now(ZoneId.of("Pacific/Kiritimati"));
        LocalDate dateNiue = LocalDate.now(ZoneId.of("Pacific/Niue"));
        org.junit.jupiter.api.Assertions.assertTrue(dateKiritimati.isAfter(dateNiue));

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "THIS_WEEK")
                        .param("timezone", "Pacific/Kiritimati"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value(dateKiritimati.toString()));

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "THIS_WEEK")
                        .param("timezone", "Pacific/Niue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value(dateNiue.toString()));
    }

    // =========================================================================
    // Tests 49 to 54: Timezone-Aware Status Event Query Boundary Semantics (UTC)
    // =========================================================================

    @Test
    @WithMockUser(username = "owner")
    void test49_EuropeBudapestEventAfterLocalMidnightIncludedInLocalDay() throws Exception {
        // Europe/Budapest on 2026-09-05 is CEST (UTC+2).
        // Event at local time 2026-09-05T00:30:00 is stored as UTC 2026-09-04T22:30:00.
        // Even though the UTC date is Sep 4, in Budapest local time it occurred on Sep 5.
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("BudapestCorp").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());

        LocalDateTime utcChangedAt = LocalDateTime.of(2026, 9, 4, 22, 30, 0);
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .toStatus(ApplicationStatus.INTERVIEW)
                .changedAt(utcChangedAt)
                .note("Interview scheduled")
                .build());

        // Query single-day CUSTOM range for 2026-09-05 in Europe/Budapest
        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-05")
                        .param("to", "2026-09-05")
                        .param("timezone", "Europe/Budapest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.interviewsReached").value(1))
                .andExpect(jsonPath("$.current.responsesRecorded").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test50_AmericaNewYorkEventLateLocalEveningIncludedInLocalDay() throws Exception {
        // America/New_York on 2026-09-05 is EDT (UTC-4).
        // Event at local time 2026-09-05T21:30:00 is stored as UTC 2026-09-06T01:30:00.
        // Even though the UTC date is Sep 6, in New York local time it occurred on Sep 5.
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("NYC_Corp").jobTitle("Dev").status(ApplicationStatus.OFFER).build());

        LocalDateTime utcChangedAt = LocalDateTime.of(2026, 9, 6, 1, 30, 0);
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .toStatus(ApplicationStatus.OFFER)
                .changedAt(utcChangedAt)
                .note("Offer received late evening")
                .build());

        // Query single-day CUSTOM range for 2026-09-05 in America/New_York
        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-05")
                        .param("to", "2026-09-05")
                        .param("timezone", "America/New_York"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.offersReached").value(1))
                .andExpect(jsonPath("$.current.responsesRecorded").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test51_EventExactlyAtLocalStartBoundaryIncluded() throws Exception {
        // Europe/Budapest on 2026-09-05 starts at local midnight: 2026-09-05T00:00:00+02:00 -> UTC 2026-09-04T22:00:00.
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("ExactStartCorp").jobTitle("Dev").status(ApplicationStatus.ASSESSMENT).build());

        LocalDateTime utcStart = LocalDateTime.of(2026, 9, 4, 22, 0, 0);
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .toStatus(ApplicationStatus.ASSESSMENT)
                .changedAt(utcStart)
                .note("Assessment at exact start boundary")
                .build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-05")
                        .param("to", "2026-09-05")
                        .param("timezone", "Europe/Budapest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.assessmentsReached").value(1))
                .andExpect(jsonPath("$.current.responsesRecorded").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test52_EventExactlyAtLocalEndExclusiveBoundaryExcluded() throws Exception {
        // Europe/Budapest for 2026-09-05 ends exclusively at next day's local midnight: 2026-09-06T00:00:00+02:00 -> UTC 2026-09-05T22:00:00.
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("ExactEndCorp").jobTitle("Dev").status(ApplicationStatus.REJECTED).build());

        LocalDateTime utcEndExclusive = LocalDateTime.of(2026, 9, 5, 22, 0, 0);
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .toStatus(ApplicationStatus.REJECTED)
                .changedAt(utcEndExclusive)
                .note("Rejection at exact end exclusive boundary")
                .build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-05")
                        .param("to", "2026-09-05")
                        .param("timezone", "Europe/Budapest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.rejectionsRecorded").value(0))
                .andExpect(jsonPath("$.current.responsesRecorded").value(0));
    }

    @Test
    @WithMockUser(username = "owner")
    void test53_CustomRangeUsesSuppliedTimezoneForStatusHistoryBoundaries() throws Exception {
        // Asia/Kolkata is UTC+05:30.
        // CUSTOM range: 2026-09-01 to 2026-09-05 in Asia/Kolkata:
        // Local start: 2026-09-01T00:00:00+05:30 -> UTC 2026-08-31T18:30:00.
        // Local endExclusive: 2026-09-06T00:00:00+05:30 -> UTC 2026-09-05T18:30:00.

        // App 1: UTC 2026-08-31T19:00:00 is on local date Sep 1 in Kolkata (00:30) -> INCLUDED.
        JobApplication app1 = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("KolkataApp1").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app1)
                .toStatus(ApplicationStatus.INTERVIEW)
                .changedAt(LocalDateTime.of(2026, 8, 31, 19, 0, 0))
                .note("Interview round 1")
                .build());

        // App 2: UTC 2026-09-05T19:00:00 is on local date Sep 6 in Kolkata (00:30) -> EXCLUDED.
        JobApplication app2 = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("KolkataApp2").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app2)
                .toStatus(ApplicationStatus.INTERVIEW)
                .changedAt(LocalDateTime.of(2026, 9, 5, 19, 0, 0))
                .note("Interview round 2")
                .build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-05")
                        .param("timezone", "Asia/Kolkata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-09-01"))
                .andExpect(jsonPath("$.to").value("2026-09-05"))
                .andExpect(jsonPath("$.current.interviewsReached").value(1)) // only app1
                .andExpect(jsonPath("$.current.responsesRecorded").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test54_DateAppliedMetricsRemainBasedDirectlyOnLocalDate() throws Exception {
        // App submitted with calendar date 2026-09-05
        applicationRepository.save(JobApplication.builder()
                .user(testUser)
                .companyName("DateCorp")
                .jobTitle("Dev")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.of(2026, 9, 5))
                .build());

        // Querying for 2026-09-05 with different timezones must consistently return 1
        for (String tz : new String[]{"America/New_York", "Europe/Budapest", "Asia/Tokyo", "UTC"}) {
            mockMvc.perform(get("/api/dashboard/period")
                            .param("range", "CUSTOM")
                            .param("from", "2026-09-05")
                            .param("to", "2026-09-05")
                            .param("timezone", tz))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.current.applicationsSubmitted").value(1));
        }
    }

    @Test
    @WithMockUser(username = "owner")
    void test55_InterviewToInterviewExcluded() throws Exception {
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("NoOpInterviewCorp").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());

        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .fromStatus(ApplicationStatus.INTERVIEW)
                .toStatus(ApplicationStatus.INTERVIEW)
                .changedAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .note("No-op interview audit log")
                .build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.interviewsReached").value(0))
                .andExpect(jsonPath("$.current.responsesRecorded").value(0));
    }

    @Test
    @WithMockUser(username = "owner")
    void test56_AssessmentToAssessmentExcluded() throws Exception {
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("NoOpAssessmentCorp").jobTitle("Dev").status(ApplicationStatus.ASSESSMENT).build());

        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .fromStatus(ApplicationStatus.ASSESSMENT)
                .toStatus(ApplicationStatus.ASSESSMENT)
                .changedAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .note("No-op assessment audit log")
                .build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.assessmentsReached").value(0))
                .andExpect(jsonPath("$.current.responsesRecorded").value(0));
    }

    @Test
    @WithMockUser(username = "owner")
    void test57_RejectedToRejectedExcluded() throws Exception {
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("NoOpRejectedCorp").jobTitle("Dev").status(ApplicationStatus.REJECTED).build());

        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .fromStatus(ApplicationStatus.REJECTED)
                .toStatus(ApplicationStatus.REJECTED)
                .changedAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .note("No-op rejection audit log")
                .build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.rejectionsRecorded").value(0))
                .andExpect(jsonPath("$.current.responsesRecorded").value(0));
    }

    @Test
    @WithMockUser(username = "owner")
    void test58_SameStatusEventInLaterPeriodDoesNotMakeStatusAppearReachedAgain() throws Exception {
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("RepeatStatusCorp").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());

        // Reached interview in August 2026
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .fromStatus(ApplicationStatus.APPLIED)
                .toStatus(ApplicationStatus.INTERVIEW)
                .changedAt(LocalDateTime.of(2026, 8, 15, 10, 0))
                .note("Real transition to interview")
                .build());

        // Same-status event in September 2026 (e.g. follow-up / email processed)
        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .fromStatus(ApplicationStatus.INTERVIEW)
                .toStatus(ApplicationStatus.INTERVIEW)
                .changedAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .note("Email Import processed: round 2 info")
                .build());

        // In September 2026, INTERVIEW should NOT appear reached again
        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.interviewsReached").value(0))
                .andExpect(jsonPath("$.current.responsesRecorded").value(0));
    }

    @Test
    @WithMockUser(username = "owner")
    void test59_RealInReviewToInterviewCounts() throws Exception {
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("InReviewToInterviewCorp").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());

        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .fromStatus(ApplicationStatus.IN_REVIEW)
                .toStatus(ApplicationStatus.INTERVIEW)
                .changedAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .note("Passed review to interview")
                .build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.interviewsReached").value(1))
                .andExpect(jsonPath("$.current.responsesRecorded").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test60_FromStatusNullRemainsEligibleRecordedInitialState() throws Exception {
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("InitialInterviewCorp").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());

        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .fromStatus(null)
                .toStatus(ApplicationStatus.INTERVIEW)
                .changedAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .note("Application created directly in interview state")
                .build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.interviewsReached").value(1))
                .andExpect(jsonPath("$.current.responsesRecorded").value(1));
    }

    @Test
    @WithMockUser(username = "owner")
    void test61_EmailImportAuditRecordWithSameStatusExcluded() throws Exception {
        JobApplication app = applicationRepository.save(JobApplication.builder()
                .user(testUser).companyName("EmailAuditCorp").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());

        statusHistoryRepository.save(StatusHistory.builder()
                .jobApplication(app)
                .fromStatus(ApplicationStatus.INTERVIEW)
                .toStatus(ApplicationStatus.INTERVIEW)
                .changedAt(LocalDateTime.of(2026, 9, 2, 10, 0))
                .note("Email Import processed: Phone Screen confirmed")
                .build());

        mockMvc.perform(get("/api/dashboard/period")
                        .param("range", "CUSTOM")
                        .param("from", "2026-09-01")
                        .param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.interviewsReached").value(0))
                .andExpect(jsonPath("$.current.responsesRecorded").value(0));
    }

    @Test
    void test62_PresetResolutionAndFairComparisonWindowsDirectAudit() {
        // 1. THIS_WEEK on Wednesday 2026-09-02
        LocalDate wednesday = LocalDate.of(2026, 9, 2);
        DashboardService.ResolvedPeriod thisWeek = dashboardService.resolvePeriod(PeriodRange.THIS_WEEK, null, null, wednesday);
        assertEquals(LocalDate.of(2026, 8, 31), thisWeek.from()); // Monday
        assertEquals(LocalDate.of(2026, 9, 2), thisWeek.to());   // Wednesday
        assertEquals(LocalDate.of(2026, 8, 24), thisWeek.previousFrom()); // Prev Monday
        assertEquals(LocalDate.of(2026, 8, 26), thisWeek.previousTo());   // Prev Wednesday

        // 2. LAST_WEEK on 2026-09-02
        DashboardService.ResolvedPeriod lastWeek = dashboardService.resolvePeriod(PeriodRange.LAST_WEEK, null, null, wednesday);
        assertEquals(LocalDate.of(2026, 8, 24), lastWeek.from()); // Mon
        assertEquals(LocalDate.of(2026, 8, 30), lastWeek.to());   // Sun
        assertEquals(LocalDate.of(2026, 8, 17), lastWeek.previousFrom());
        assertEquals(LocalDate.of(2026, 8, 23), lastWeek.previousTo());

        // 3. THIS_MONTH on 2026-09-15
        LocalDate midMonth = LocalDate.of(2026, 9, 15);
        DashboardService.ResolvedPeriod thisMonth = dashboardService.resolvePeriod(PeriodRange.THIS_MONTH, null, null, midMonth);
        assertEquals(LocalDate.of(2026, 9, 1), thisMonth.from());
        assertEquals(LocalDate.of(2026, 9, 15), thisMonth.to());
        assertEquals(LocalDate.of(2026, 8, 1), thisMonth.previousFrom());
        assertEquals(LocalDate.of(2026, 8, 15), thisMonth.previousTo());

        // 4. THIS_MONTH shorter previous month clamp: March 31 -> Feb 28
        LocalDate march31 = LocalDate.of(2026, 3, 31);
        DashboardService.ResolvedPeriod marchPeriod = dashboardService.resolvePeriod(PeriodRange.THIS_MONTH, null, null, march31);
        assertEquals(LocalDate.of(2026, 3, 1), marchPeriod.from());
        assertEquals(LocalDate.of(2026, 3, 31), marchPeriod.to());
        assertEquals(LocalDate.of(2026, 2, 1), marchPeriod.previousFrom());
        assertEquals(LocalDate.of(2026, 2, 28), marchPeriod.previousTo()); // Clamped to 28

        // 5. LAST_MONTH on 2026-09-15
        DashboardService.ResolvedPeriod lastMonth = dashboardService.resolvePeriod(PeriodRange.LAST_MONTH, null, null, midMonth);
        assertEquals(LocalDate.of(2026, 8, 1), lastMonth.from());
        assertEquals(LocalDate.of(2026, 8, 31), lastMonth.to());
        assertEquals(LocalDate.of(2026, 7, 1), lastMonth.previousFrom());
        assertEquals(LocalDate.of(2026, 7, 31), lastMonth.previousTo());

        // 6. LAST_30_DAYS on 2026-09-30
        LocalDate sep30 = LocalDate.of(2026, 9, 30);
        DashboardService.ResolvedPeriod last30 = dashboardService.resolvePeriod(PeriodRange.LAST_30_DAYS, null, null, sep30);
        assertEquals(LocalDate.of(2026, 9, 1), last30.from());
        assertEquals(LocalDate.of(2026, 9, 30), last30.to());
        assertEquals(LocalDate.of(2026, 8, 2), last30.previousFrom());
        assertEquals(LocalDate.of(2026, 8, 31), last30.previousTo());

        // 7. LAST_90_DAYS on 2026-09-30
        DashboardService.ResolvedPeriod last90 = dashboardService.resolvePeriod(PeriodRange.LAST_90_DAYS, null, null, sep30);
        assertEquals(LocalDate.of(2026, 7, 3), last90.from());
        assertEquals(LocalDate.of(2026, 9, 30), last90.to());
        assertEquals(LocalDate.of(2026, 4, 4), last90.previousFrom());
        assertEquals(LocalDate.of(2026, 7, 2), last90.previousTo());

        // 8. YTD on 2026-09-05
        LocalDate sep5 = LocalDate.of(2026, 9, 5);
        DashboardService.ResolvedPeriod ytd = dashboardService.resolvePeriod(PeriodRange.YTD, null, null, sep5);
        assertEquals(LocalDate.of(2026, 1, 1), ytd.from());
        assertEquals(LocalDate.of(2026, 9, 5), ytd.to());
        assertEquals(LocalDate.of(2025, 1, 1), ytd.previousFrom());
        assertEquals(LocalDate.of(2025, 9, 5), ytd.previousTo());

        // 9. YTD leap-year handling: 2024-02-29 -> 2023-02-28
        LocalDate leapDay = LocalDate.of(2024, 2, 29);
        DashboardService.ResolvedPeriod ytdLeap = dashboardService.resolvePeriod(PeriodRange.YTD, null, null, leapDay);
        assertEquals(LocalDate.of(2024, 1, 1), ytdLeap.from());
        assertEquals(LocalDate.of(2024, 2, 29), ytdLeap.to());
        assertEquals(LocalDate.of(2023, 1, 1), ytdLeap.previousFrom());
        assertEquals(LocalDate.of(2023, 2, 28), ytdLeap.previousTo()); // Leap day clamped to Feb 28

        // 10. CUSTOM equal-duration range: 2026-09-10 to 2026-09-14 (5 days)
        DashboardService.ResolvedPeriod custom = dashboardService.resolvePeriod(PeriodRange.CUSTOM,
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 14), sep5);
        assertEquals(LocalDate.of(2026, 9, 10), custom.from());
        assertEquals(LocalDate.of(2026, 9, 14), custom.to());
        assertEquals(LocalDate.of(2026, 9, 5), custom.previousFrom());
        assertEquals(LocalDate.of(2026, 9, 9), custom.previousTo());

        // 11. ALL_TIME comparison disabled
        DashboardService.ResolvedPeriod allTime = dashboardService.resolvePeriod(PeriodRange.ALL_TIME, null, null, sep5);
        assertNull(allTime.from());
        assertNull(allTime.to());
        assertNull(allTime.previousFrom());
        assertNull(allTime.previousTo());
    }
}
