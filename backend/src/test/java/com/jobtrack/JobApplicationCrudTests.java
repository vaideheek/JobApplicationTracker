package com.jobtrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.StatusHistory;
import com.jobtrack.entity.User;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusHistoryRepository;
import com.jobtrack.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class JobApplicationCrudTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository applicationRepository;

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        userRepository.deleteAll();

        // Seed test user "owner" so MockMvc lookup resolves it successfully
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
    }

    @Test
    @WithMockUser(username = "owner")
    void testCreateJobApplication() throws Exception {
        JobApplication app = JobApplication.builder()
                .companyName("Innovatech")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/applications")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(app)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.companyName").value("Innovatech"))
                .andExpect(jsonPath("$.jobTitle").value("Developer"));
    }

    @Test
    @WithMockUser(username = "owner")
    void testGetJobApplications() throws Exception {
        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("Innovatech")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();
        applicationRepository.save(app);

        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].companyName").value("Innovatech"));
    }

    @Test
    @WithMockUser(username = "owner")
    void testUpdateJobApplication() throws Exception {
        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("Innovatech")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();
        JobApplication saved = applicationRepository.save(app);

        saved.setJobTitle("Senior Developer");

        mockMvc.perform(put("/api/applications/" + saved.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saved)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Senior Developer"));
    }

    @Test
    @WithMockUser(username = "owner")
    void testDeleteJobApplication() throws Exception {
        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("Innovatech")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();
        JobApplication saved = applicationRepository.save(app);

        mockMvc.perform(delete("/api/applications/" + saved.getId())
                .with(csrf()))
                .andExpect(status().isNoContent());

        assertFalse(applicationRepository.existsById(saved.getId()));
    }

    @Test
    @WithMockUser(username = "owner")
    void testCreateAndPreserveMultilineJobDescriptionAndOriginalJobUrl() throws Exception {
        String multilineDescription = "About the Role:\n" +
                "We are seeking a Senior Engineer & Architect.\n\n" +
                "Requirements & Responsibilities:\n" +
                "- 5+ years experience with Java/Spring Boot <Cloud-native>\n" +
                "- Expertise with 'PostgreSQL' and \"REST APIs\"\n" +
                "- Salary target: $150k - $180k © 2026 🎉\n\n" +
                "Equal Opportunity Employer.";

        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("TechForward Inc")
                .jobTitle("Staff Engineer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.of(2026, 8, 1))
                .jobUrl("https://linkedin.com/jobs/view/123456")
                .originalJobUrl("https://techforward.com/careers/staff-eng-789")
                .jobDescription(multilineDescription)
                .jobDescriptionSummary("Architectural leadership role in cloud systems")
                .matchScore(92)
                .matchedSkills("Java, Spring Boot, PostgreSQL")
                .missingSkills("GraphQL")
                .build();
        JobApplication saved = applicationRepository.save(app);

        // Verify loaded data has both fields
        mockMvc.perform(get("/api/applications/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobDescription").value(multilineDescription))
                .andExpect(jsonPath("$.originalJobUrl").value("https://techforward.com/careers/staff-eng-789"))
                .andExpect(jsonPath("$.jobUrl").value("https://linkedin.com/jobs/view/123456"))
                .andExpect(jsonPath("$.jobDescriptionSummary").value("Architectural leadership role in cloud systems"))
                .andExpect(jsonPath("$.matchScore").value(92));

        // Now simulate an edit to another field (e.g. status and stage)
        // Ensure jobDescription, originalJobUrl, and derived fields are not erased!
        com.jobtrack.dto.JobApplicationRequest updateRequest = com.jobtrack.dto.JobApplicationRequest.builder()
                .companyName("TechForward Inc")
                .jobTitle("Staff Engineer")
                .status(ApplicationStatus.INTERVIEW)
                .stage("Technical Round 1")
                .dateApplied(LocalDate.of(2026, 8, 1))
                .jobUrl("https://linkedin.com/jobs/view/123456")
                .originalJobUrl("https://techforward.com/careers/staff-eng-789")
                .jobDescription(multilineDescription)
                .build();

        mockMvc.perform(put("/api/applications/" + saved.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INTERVIEW"))
                .andExpect(jsonPath("$.stage").value("Technical Round 1"))
                .andExpect(jsonPath("$.jobDescription").value(multilineDescription))
                .andExpect(jsonPath("$.originalJobUrl").value("https://techforward.com/careers/staff-eng-789"))
                .andExpect(jsonPath("$.jobUrl").value("https://linkedin.com/jobs/view/123456"))
                .andExpect(jsonPath("$.jobDescriptionSummary").value("Architectural leadership role in cloud systems"))
                .andExpect(jsonPath("$.matchScore").value(92))
                .andExpect(jsonPath("$.matchedSkills").value("Java, Spring Boot, PostgreSQL"))
                .andExpect(jsonPath("$.missingSkills").value("GraphQL"));
    }

    @Test
    @WithMockUser(username = "owner")
    void testEditJobDescriptionItselfPersists() throws Exception {
        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("CloudCorp")
                .jobTitle("DevOps Specialist")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.of(2026, 8, 10))
                .jobDescription("Initial description")
                .originalJobUrl("https://cloudcorp.com/apply")
                .build();
        JobApplication saved = applicationRepository.save(app);

        String newDescription = "Updated description with multiple lines:\n\nParagraph 2 with details.";

        com.jobtrack.dto.JobApplicationRequest updateRequest = com.jobtrack.dto.JobApplicationRequest.builder()
                .companyName("CloudCorp")
                .jobTitle("DevOps Specialist")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.of(2026, 8, 10))
                .jobDescription(newDescription)
                .originalJobUrl("https://cloudcorp.com/apply-new")
                .build();

        mockMvc.perform(put("/api/applications/" + saved.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobDescription").value(newDescription))
                .andExpect(jsonPath("$.originalJobUrl").value("https://cloudcorp.com/apply-new"));
    }

    @Test
    @WithMockUser(username = "owner")
    void testOptionalFieldsEmptyRemainEditable() throws Exception {
        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("StartupX")
                .jobTitle("Frontend Dev")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.of(2026, 8, 15))
                .jobDescription(null)
                .originalJobUrl(null)
                .build();
        JobApplication saved = applicationRepository.save(app);

        // Edit application and populate previously empty fields
        com.jobtrack.dto.JobApplicationRequest updateRequest = com.jobtrack.dto.JobApplicationRequest.builder()
                .companyName("StartupX")
                .jobTitle("Frontend Dev")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.of(2026, 8, 15))
                .jobDescription("Added later")
                .originalJobUrl("https://startupx.io/job/1")
                .build();

        mockMvc.perform(put("/api/applications/" + saved.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobDescription").value("Added later"))
                .andExpect(jsonPath("$.originalJobUrl").value("https://startupx.io/job/1"));
    }

    @Test
    @WithMockUser(username = "owner")
    void testUpdateApplication_StatusChangeWithEarlierOccurredOnDate() throws Exception {
        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("AcmeCorp")
                .jobTitle("Backend Engineer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.of(2026, 9, 1))
                .build();
        JobApplication saved = applicationRepository.save(app);

        // Record a rejection that occurred on Sep 10, updated in JobTrack today
        LocalDate occurredDate = LocalDate.of(2026, 9, 10);
        com.jobtrack.dto.JobApplicationRequest updateRequest = com.jobtrack.dto.JobApplicationRequest.builder()
                .companyName("AcmeCorp")
                .jobTitle("Backend Engineer")
                .status(ApplicationStatus.REJECTED)
                .statusChangeDate(occurredDate)
                .build();

        mockMvc.perform(put("/api/applications/" + saved.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Verify status history
        mockMvc.perform(get("/api/applications/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.statusHistory[0].fromStatus").value("APPLIED"))
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("REJECTED"))
                .andExpect(jsonPath("$.statusHistory[0].occurredOn").value("2026-09-10"))
                .andExpect(jsonPath("$.statusHistory[0].changedAt").exists());

        // Verify database entity directly
        var historyEntries = statusHistoryRepository.findByJobApplicationIdOrderByChangedAtDesc(saved.getId());
        assertEquals(1, historyEntries.size());
        assertEquals(occurredDate, historyEntries.get(0).getOccurredOn());
        assertNotNull(historyEntries.get(0).getChangedAt());
    }

    @Test
    @WithMockUser(username = "owner")
    void testUpdateApplication_UnchangedStatusDoesNotCreateStatusHistory() throws Exception {
        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("BetaTech")
                .jobTitle("Platform Engineer")
                .status(ApplicationStatus.INTERVIEW)
                .dateApplied(LocalDate.of(2026, 9, 1))
                .build();
        JobApplication saved = applicationRepository.save(app);

        // Pre-existing initial history entry
        StatusHistory initial = StatusHistory.builder()
                .jobApplication(saved)
                .fromStatus(null)
                .toStatus(ApplicationStatus.INTERVIEW)
                .occurredOn(LocalDate.of(2026, 9, 5))
                .changedAt(LocalDateTime.now())
                .note("Application created")
                .build();
        statusHistoryRepository.save(initial);

        // Edit other application details without changing status
        com.jobtrack.dto.JobApplicationRequest updateRequest = com.jobtrack.dto.JobApplicationRequest.builder()
                .companyName("BetaTech Inc.")
                .jobTitle("Lead Platform Engineer")
                .notes("Added new notes during edit")
                .status(ApplicationStatus.INTERVIEW)
                .build();

        mockMvc.perform(put("/api/applications/" + saved.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("BetaTech Inc."))
                .andExpect(jsonPath("$.jobTitle").value("Lead Platform Engineer"));

        // Verify NO new status history entry was created
        var historyEntries = statusHistoryRepository.findByJobApplicationIdOrderByChangedAtDesc(saved.getId());
        assertEquals(1, historyEntries.size(), "Unchanged status edit must not create a new status history entry");
    }

    @Test
    @WithMockUser(username = "owner")
    void testUpdateApplication_FutureStatusChangeDateRejected() throws Exception {
        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("GammaCorp")
                .jobTitle("Data Scientist")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.of(2026, 9, 1))
                .build();
        JobApplication saved = applicationRepository.save(app);

        // Date strictly in the future for any timezone on Earth (e.g. 5 days from max global today)
        LocalDate futureDate = LocalDate.now(ZoneId.of("Pacific/Kiritimati")).plusDays(5);

        com.jobtrack.dto.JobApplicationRequest updateRequest = com.jobtrack.dto.JobApplicationRequest.builder()
                .companyName("GammaCorp")
                .jobTitle("Data Scientist")
                .status(ApplicationStatus.REJECTED)
                .statusChangeDate(futureDate)
                .build();

        mockMvc.perform(put("/api/applications/" + saved.getId())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Status change date cannot be in the future")));
    }

    @Test
    @WithMockUser(username = "owner")
    void testCreateApplication_DirectlyInLaterStatus_WithoutStatusDate_OccurredOnIsNull() throws Exception {
        com.jobtrack.dto.JobApplicationRequest request = com.jobtrack.dto.JobApplicationRequest.builder()
                .companyName("DeltaSystems")
                .jobTitle("Systems Architect")
                .status(ApplicationStatus.REJECTED)
                .dateApplied(LocalDate.of(2026, 9, 1))
                .build();

        String responseJson = mockMvc.perform(post("/api/applications")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(responseJson).get("id").asLong();

        // Check history
        mockMvc.perform(get("/api/applications/" + appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("REJECTED"))
                .andExpect(jsonPath("$.statusHistory[0].occurredOn").doesNotExist());

        var history = statusHistoryRepository.findByJobApplicationIdOrderByChangedAtDesc(appId);
        assertEquals(1, history.size());
        assertNull(history.get(0).getOccurredOn(), "Initial status history for later stage without explicit date must have null occurredOn");
    }

    @Test
    @WithMockUser(username = "owner")
    void testCreateApplication_DirectlyInLaterStatus_WithStatusDate() throws Exception {
        LocalDate explicitDate = LocalDate.of(2026, 9, 8);
        com.jobtrack.dto.JobApplicationRequest request = com.jobtrack.dto.JobApplicationRequest.builder()
                .companyName("EpsilonLLC")
                .jobTitle("Security Analyst")
                .status(ApplicationStatus.OFFER)
                .dateApplied(LocalDate.of(2026, 9, 1))
                .statusChangeDate(explicitDate)
                .build();

        String responseJson = mockMvc.perform(post("/api/applications")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(responseJson).get("id").asLong();

        mockMvc.perform(get("/api/applications/" + appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("OFFER"))
                .andExpect(jsonPath("$.statusHistory[0].occurredOn").value("2026-09-08"));
    }

    @Test
    @WithMockUser(username = "owner")
    void testCreateApplication_InitialAppliedStatus_UsesDateApplied() throws Exception {
        LocalDate applyDate = LocalDate.of(2026, 8, 25);
        com.jobtrack.dto.JobApplicationRequest request = com.jobtrack.dto.JobApplicationRequest.builder()
                .companyName("ZetaLabs")
                .jobTitle("ML Engineer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(applyDate)
                .build();

        String responseJson = mockMvc.perform(post("/api/applications")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long appId = objectMapper.readTree(responseJson).get("id").asLong();

        mockMvc.perform(get("/api/applications/" + appId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("APPLIED"))
                .andExpect(jsonPath("$.statusHistory[0].occurredOn").value("2026-08-25"));
    }

    @Test
    @WithMockUser(username = "owner")
    void testGetApplication_LegacyHistoryRecordWithNullOccurredOn() throws Exception {
        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("LegacyCo")
                .jobTitle("QA Specialist")
                .status(ApplicationStatus.INTERVIEW)
                .build();
        JobApplication saved = applicationRepository.save(app);

        // Seed legacy record with null occurredOn
        StatusHistory legacyHistory = StatusHistory.builder()
                .jobApplication(saved)
                .fromStatus(ApplicationStatus.APPLIED)
                .toStatus(ApplicationStatus.INTERVIEW)
                .occurredOn(null)
                .changedAt(LocalDateTime.of(2026, 8, 15, 14, 30))
                .note("Legacy transition")
                .build();
        statusHistoryRepository.save(legacyHistory);

        mockMvc.perform(get("/api/applications/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusHistory", hasSize(1)))
                .andExpect(jsonPath("$.statusHistory[0].occurredOn").doesNotExist())
                .andExpect(jsonPath("$.statusHistory[0].changedAt").value("2026-08-15T14:30:00"));
    }
}
