package com.jobtrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

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
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        authToken = jwtService.generateToken("admin@test.com");
    }

    @Test
    void testCreateJobApplication() throws Exception {
        JobApplication app = JobApplication.builder()
                .companyName("Innovatech")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(app)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.companyName").value("Innovatech"))
                .andExpect(jsonPath("$.jobTitle").value("Developer"));
    }

    @Test
    void testGetJobApplications() throws Exception {
        JobApplication app = JobApplication.builder()
                .companyName("Innovatech")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();
        applicationRepository.save(app);

        mockMvc.perform(get("/api/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].companyName").value("Innovatech"));
    }

    @Test
    void testUpdateJobApplication() throws Exception {
        JobApplication app = JobApplication.builder()
                .companyName("Innovatech")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();
        JobApplication saved = applicationRepository.save(app);

        saved.setJobTitle("Senior Developer");

        mockMvc.perform(put("/api/applications/" + saved.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(saved)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Senior Developer"));
    }

    @Test
    void testDeleteJobApplication() throws Exception {
        JobApplication app = JobApplication.builder()
                .companyName("Innovatech")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();
        JobApplication saved = applicationRepository.save(app);

        mockMvc.perform(delete("/api/applications/" + saved.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authToken))
                .andExpect(status().isNoContent());

        assertFalse(applicationRepository.existsById(saved.getId()));
    }
}
