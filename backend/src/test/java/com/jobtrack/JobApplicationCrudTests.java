package com.jobtrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.enums.ApplicationStatus;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
}
