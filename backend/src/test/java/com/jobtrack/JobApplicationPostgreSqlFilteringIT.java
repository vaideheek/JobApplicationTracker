package com.jobtrack;

import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Focused PostgreSQL regression test for filtering with dynamic Specification.
 * Verifies that PostgreSQL executes queries cleanly without SQLState 42P18
 * ("could not determine data type of parameter").
 *
 * Runs automatically in environments with Docker (such as GitHub Actions CI),
 * and is safely skipped if Docker is not available in the local environment.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@EnabledIf("isDockerAvailable")
public class JobApplicationPostgreSqlFilteringIT {

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JobApplicationRepository applicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .username("pg_owner")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("Postgres Owner")
                .role("ROLE_USER")
                .enabled(true)
                .demoAccount(false)
                .createdAt(LocalDateTime.now())
                .build();
        testUser = userRepository.save(testUser);

        // App 1: In range, INTERVIEW, HIGH
        applicationRepository.save(JobApplication.builder()
                .user(testUser)
                .companyName("Postgres Acme")
                .jobTitle("Backend Engineer")
                .status(ApplicationStatus.INTERVIEW)
                .priority(ApplicationPriority.HIGH)
                .dateApplied(LocalDate.of(2026, 8, 10))
                .build());

        // App 2: Out of range (date), APPLIED, LOW
        applicationRepository.save(JobApplication.builder()
                .user(testUser)
                .companyName("Postgres Beta")
                .jobTitle("Frontend Engineer")
                .status(ApplicationStatus.APPLIED)
                .priority(ApplicationPriority.LOW)
                .dateApplied(LocalDate.of(2026, 7, 1))
                .build());

        // App 3: In range, REJECTED, MEDIUM
        applicationRepository.save(JobApplication.builder()
                .user(testUser)
                .companyName("Postgres Gamma")
                .jobTitle("DevOps Engineer")
                .status(ApplicationStatus.REJECTED)
                .priority(ApplicationPriority.MEDIUM)
                .dateApplied(LocalDate.of(2026, 8, 15))
                .build());
    }

    @Test
    @WithMockUser(username = "pg_owner")
    void testNoFilter_ReturnsAllUserApplications() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(3)));
    }

    @Test
    @WithMockUser(username = "pg_owner")
    void testDateRangeFilter_ReturnsOnlyMatchingApplications() throws Exception {
        mockMvc.perform(get("/api/applications")
                .param("dateFrom", "2026-08-01")
                .param("dateTo", "2026-08-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "pg_owner")
    void testStatusFilter_ReturnsOnlyMatchingStatus() throws Exception {
        mockMvc.perform(get("/api/applications")
                .param("status", "INTERVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].companyName", is("Postgres Acme")));
    }

    @Test
    @WithMockUser(username = "pg_owner")
    void testCombinedFilter_ReturnsAccurateIntersection() throws Exception {
        mockMvc.perform(get("/api/applications")
                .param("search", "Acme")
                .param("status", "INTERVIEW")
                .param("priority", "HIGH")
                .param("dateFrom", "2026-08-01")
                .param("dateTo", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].companyName", is("Postgres Acme")));
    }
}
