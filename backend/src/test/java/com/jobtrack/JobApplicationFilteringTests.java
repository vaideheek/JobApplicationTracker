package com.jobtrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.entity.ApplicationDocument;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.enums.DocumentType;
import com.jobtrack.repository.ApplicationDocumentRepository;
import com.jobtrack.repository.JobApplicationRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class JobApplicationFilteringTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository applicationRepository;

    @Autowired
    private ApplicationDocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
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
    void test1_OwnershipIsolation() throws Exception {
        // App for owner
        JobApplication appOwner = JobApplication.builder()
                .user(testUser)
                .companyName("Owner Corp")
                .jobTitle("Engineer")
                .status(ApplicationStatus.APPLIED)
                .build();
        applicationRepository.save(appOwner);

        // App for other
        JobApplication appOther = JobApplication.builder()
                .user(otherUser)
                .companyName("Other Corp")
                .jobTitle("Designer")
                .status(ApplicationStatus.APPLIED)
                .build();
        applicationRepository.save(appOther);

        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("Owner Corp"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test2_SearchByCompanyName() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("Google").jobTitle("Engineer").status(ApplicationStatus.APPLIED).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("Apple").jobTitle("Engineer").status(ApplicationStatus.APPLIED).build());

        mockMvc.perform(get("/api/applications").param("search", "oog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("Google"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test3_SearchByJobTitle() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Frontend Developer").status(ApplicationStatus.APPLIED).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Backend Specialist").status(ApplicationStatus.APPLIED).build());

        mockMvc.perform(get("/api/applications").param("search", "front"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("A"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test4_SearchByLocation() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").location("New York").status(ApplicationStatus.APPLIED).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").location("San Francisco").status(ApplicationStatus.APPLIED).build());

        mockMvc.perform(get("/api/applications").param("search", "york"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("A"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test5_SearchBySource() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").source("LinkedIn").status(ApplicationStatus.APPLIED).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").source("Indeed").status(ApplicationStatus.APPLIED).build());

        mockMvc.perform(get("/api/applications").param("search", "linked"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("A"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test6_StatusFiltering() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.INTERVIEW).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.APPLIED).build());

        mockMvc.perform(get("/api/applications").param("status", "INTERVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("A"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test7_PriorityFiltering() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).priority(ApplicationPriority.HIGH).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.APPLIED).priority(ApplicationPriority.LOW).build());

        mockMvc.perform(get("/api/applications").param("priority", "HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("A"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test8_DateFrom() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 1)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 7, 30)).build());

        mockMvc.perform(get("/api/applications").param("dateFrom", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("A"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test9_DateTo() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 1)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 7, 30)).build());

        mockMvc.perform(get("/api/applications").param("dateTo", "2026-07-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("B"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test10_CombinedDateRange() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 5)).build());
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.APPLIED).dateApplied(LocalDate.of(2026, 8, 10)).build());

        mockMvc.perform(get("/api/applications").param("dateFrom", "2026-08-04").param("dateTo", "2026-08-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("A"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test11_HasDocuments() throws Exception {
        JobApplication appWithDoc = JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).build();
        appWithDoc = applicationRepository.save(appWithDoc);

        JobApplication appNoDoc = JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.APPLIED).build();
        applicationRepository.save(appNoDoc);

        ApplicationDocument doc = ApplicationDocument.builder()
                .jobApplication(appWithDoc)
                .fileName("cv.pdf")
                .fileType("pdf")
                .documentType(DocumentType.CV)
                .filePath("/some/path")
                .uploadedAt(LocalDateTime.now())
                .build();
        documentRepository.save(doc);

        mockMvc.perform(get("/api/applications").param("documentState", "HAS_DOCUMENTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("A"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test12_NoDocuments() throws Exception {
        JobApplication appWithDoc = JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).build();
        appWithDoc = applicationRepository.save(appWithDoc);

        JobApplication appNoDoc = JobApplication.builder().user(testUser).companyName("B").jobTitle("Dev").status(ApplicationStatus.APPLIED).build();
        applicationRepository.save(appNoDoc);

        ApplicationDocument doc = ApplicationDocument.builder()
                .jobApplication(appWithDoc)
                .fileName("cv.pdf")
                .fileType("pdf")
                .documentType(DocumentType.CV)
                .filePath("/some/path")
                .uploadedAt(LocalDateTime.now())
                .build();
        documentRepository.save(doc);

        mockMvc.perform(get("/api/applications").param("documentState", "NO_DOCUMENTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].companyName").value("B"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test13_CombinedFilters() throws Exception {
        JobApplication target = JobApplication.builder()
                .user(testUser)
                .companyName("Starlight Corp")
                .jobTitle("Senior Lead Engineer")
                .location("Seattle")
                .source("Indeed")
                .status(ApplicationStatus.INTERVIEW)
                .priority(ApplicationPriority.HIGH)
                .dateApplied(LocalDate.of(2026, 8, 15))
                .build();
        target = applicationRepository.save(target);

        JobApplication other = JobApplication.builder()
                .user(testUser)
                .companyName("Starlight Corp")
                .jobTitle("Junior Dev")
                .location("Seattle")
                .status(ApplicationStatus.APPLIED)
                .priority(ApplicationPriority.LOW)
                .build();
        applicationRepository.save(other);

        ApplicationDocument doc = ApplicationDocument.builder()
                .jobApplication(target)
                .fileName("cv.pdf")
                .fileType("pdf")
                .documentType(DocumentType.CV)
                .filePath("/some/path")
                .uploadedAt(LocalDateTime.now())
                .build();
        documentRepository.save(doc);

        mockMvc.perform(get("/api/applications")
                .param("search", "star")
                .param("status", "INTERVIEW")
                .param("priority", "HIGH")
                .param("dateFrom", "2026-08-10")
                .param("dateTo", "2026-08-20")
                .param("documentState", "HAS_DOCUMENTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].jobTitle").value("Senior Lead Engineer"));
    }

    @Test
    @WithMockUser(username = "owner")
    void test14_Pagination() throws Exception {
        for (int i = 1; i <= 20; i++) {
            applicationRepository.save(JobApplication.builder()
                    .user(testUser)
                    .companyName("Company " + String.format("%02d", i))
                    .jobTitle("Dev")
                    .status(ApplicationStatus.APPLIED)
                    .build());
        }

        // Fetch page 1 (second page) of size 15. Content must be 5 items (20 total)
        mockMvc.perform(get("/api/applications")
                .param("page", "1")
                .param("size", "15")
                .param("sortBy", "companyName")
                .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numberOfElements").value(5))
                .andExpect(jsonPath("$.totalElements").value(20));
    }

    @Test
    @WithMockUser(username = "owner")
    void test15_DeterministicOrderingWhenPrimarySortValuesTie() throws Exception {
        JobApplication app1 = applicationRepository.save(JobApplication.builder().user(testUser).companyName("Shared").jobTitle("A").status(ApplicationStatus.APPLIED).build());
        JobApplication app2 = applicationRepository.save(JobApplication.builder().user(testUser).companyName("Shared").jobTitle("B").status(ApplicationStatus.APPLIED).build());

        // Sort by companyName ASC, secondary sort is id DESC
        // Since app2 has larger id, it must appear first even though company names tie
        mockMvc.perform(get("/api/applications")
                .param("sortBy", "companyName")
                .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(app2.getId()))
                .andExpect(jsonPath("$.content[1].id").value(app1.getId()));
    }

    @Test
    @WithMockUser(username = "owner")
    void test16_SupportedSortFields() throws Exception {
        // lastUpdatedAt, dateApplied, createdAt, companyName
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).build());

        mockMvc.perform(get("/api/applications").param("sortBy", "createdAt")).andExpect(status().isOk());
        mockMvc.perform(get("/api/applications").param("sortBy", "dateApplied")).andExpect(status().isOk());
        mockMvc.perform(get("/api/applications").param("sortBy", "companyName")).andExpect(status().isOk());
        mockMvc.perform(get("/api/applications").param("sortBy", "lastUpdatedAt")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "owner")
    void test17_SupportedSortDirection() throws Exception {
        applicationRepository.save(JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).build());

        mockMvc.perform(get("/api/applications").param("sortDir", "asc")).andExpect(status().isOk());
        mockMvc.perform(get("/api/applications").param("sortDir", "desc")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "owner")
    void test18_UnsupportedSortFieldHandling() throws Exception {
        mockMvc.perform(get("/api/applications").param("sortBy", "salaryRange"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "owner")
    void test19_PageSizeRestrictions() throws Exception {
        mockMvc.perform(get("/api/applications").param("size", "20"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "owner")
    void test20_NoDuplicateApplicationRowsFromDocumentFiltering() throws Exception {
        JobApplication app = JobApplication.builder().user(testUser).companyName("A").jobTitle("Dev").status(ApplicationStatus.APPLIED).build();
        app = applicationRepository.save(app);

        // Save multiple documents
        documentRepository.save(ApplicationDocument.builder().jobApplication(app).fileName("cv.pdf").fileType("pdf").documentType(DocumentType.CV).filePath("/path1").uploadedAt(LocalDateTime.now()).build());
        documentRepository.save(ApplicationDocument.builder().jobApplication(app).fileName("cl.pdf").fileType("pdf").documentType(DocumentType.COVER_LETTER).filePath("/path2").uploadedAt(LocalDateTime.now()).build());

        mockMvc.perform(get("/api/applications").param("documentState", "HAS_DOCUMENTS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @WithMockUser(username = "owner")
    void test21_UnsupportedSortDirection() throws Exception {
        mockMvc.perform(get("/api/applications").param("sortDir", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "owner")
    void test22_NullsLastSorting() throws Exception {
        // App 1: dateApplied null
        JobApplication appNull = JobApplication.builder()
                .user(testUser)
                .companyName("Null Date Corp")
                .jobTitle("Engineer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(null)
                .build();
        appNull = applicationRepository.save(appNull);

        // App 2: dateApplied populated
        JobApplication appPopulated = JobApplication.builder()
                .user(testUser)
                .companyName("Populated Date Corp")
                .jobTitle("Engineer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.of(2026, 8, 1))
                .build();
        appPopulated = applicationRepository.save(appPopulated);

        // Sort ASC: populated date should be first, null date last
        mockMvc.perform(get("/api/applications")
                .param("sortBy", "dateApplied")
                .param("sortDir", "asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(appPopulated.getId()))
                .andExpect(jsonPath("$.content[1].id").value(appNull.getId()));

        // Sort DESC: populated date should be first, null date last (thanks to nullsLast())
        mockMvc.perform(get("/api/applications")
                .param("sortBy", "dateApplied")
                .param("sortDir", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(appPopulated.getId()))
                .andExpect(jsonPath("$.content[1].id").value(appNull.getId()));
    }
}
