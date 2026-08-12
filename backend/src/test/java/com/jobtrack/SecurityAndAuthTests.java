package com.jobtrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.controller.AuthController;
import com.jobtrack.entity.User;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.ApplicationDocument;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.enums.DocumentType;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.ApplicationDocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityAndAuthTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationDocumentRepository documentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthController authController;

    private User ownerUser;
    private User otherUser;
    private User demoUser;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        jobApplicationRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create owner user
        ownerUser = User.builder()
                .username("owner")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("Owner User")
                .role("ROLE_USER")
                .enabled(true)
                .demoAccount(false)
                .createdAt(LocalDateTime.now())
                .build();
        ownerUser = userRepository.save(ownerUser);

        // 2. Create other user
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

        // 3. Create demo user
        demoUser = User.builder()
                .username("demo")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("Demo User")
                .role("ROLE_USER")
                .enabled(true)
                .demoAccount(true)
                .createdAt(LocalDateTime.now())
                .build();
        demoUser = userRepository.save(demoUser);
    }

    private static class CsrfInfo {
        String token;
        String headerName;
        MockHttpSession session;
    }

    private CsrfInfo getCsrfTokenAndSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = csrfResult.getResponse().getContentAsString();
        CsrfInfo info = new CsrfInfo();
        info.session = session;
        info.token = objectMapper.readTree(responseContent).get("token").asText();
        info.headerName = objectMapper.readTree(responseContent).get("headerName").asText();
        return info;
    }

    @Test
    void testSignupWithCsrfSucceedsAndRotatesSession() throws Exception {
        userRepository.findByUsernameIgnoreCase("newuser").ifPresent(userRepository::delete);
        CsrfInfo csrf = getCsrfTokenAndSession();

        AuthController.SignupRequest request = new AuthController.SignupRequest();
        request.setUsername("newuser");
        request.setPassword("securePassword");
        request.setDisplayName("New User");

        MvcResult signupResult = mockMvc.perform(post("/api/auth/signup")
                .session(csrf.session)
                .header(csrf.headerName, csrf.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andReturn();

        MockHttpSession newSession = (MockHttpSession) signupResult.getRequest().getSession(false);
        assertNotNull(newSession);
        assertNotEquals(csrf.session.getId(), newSession.getId());

        // Extract the new rotated CSRF token
        org.springframework.security.web.csrf.CsrfToken newCsrf =
            (org.springframework.security.web.csrf.CsrfToken) signupResult.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        assertNotNull(newCsrf);
        assertNotEquals(csrf.token, newCsrf.getToken());

        // Prove the new token is usable by doing a write action using the new session and new token
        JobApplication app = JobApplication.builder()
                .companyName("Test Rotated")
                .jobTitle("Engineer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();

        mockMvc.perform(post("/api/applications")
                .session(newSession)
                .header(newCsrf.getHeaderName(), newCsrf.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(app)))
                .andExpect(status().isCreated());

        User saved = userRepository.findByUsernameIgnoreCase("newuser").orElseThrow();
        assertTrue(passwordEncoder.matches("securePassword", saved.getPasswordHash()));
        userRepository.delete(saved);
    }

    @Test
    void testSignupDisabledReturns403AndCreatesNoUser() throws Exception {
        org.springframework.test.util.ReflectionTestUtils.setField(authController, "signupEnabled", false);
        try {
            CsrfInfo csrf = getCsrfTokenAndSession();

            AuthController.SignupRequest request = new AuthController.SignupRequest();
            request.setUsername("disuser");
            request.setPassword("securePassword");
            request.setDisplayName("Disabled User");

            mockMvc.perform(post("/api/auth/signup")
                    .session(csrf.session)
                    .header(csrf.headerName, csrf.token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Sign-up is currently disabled."));

            assertFalse(userRepository.existsByUsernameIgnoreCase("disuser"));
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(authController, "signupEnabled", true);
        }
    }

    @Test
    void testLoginWithoutCsrfRejected() throws Exception {
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("owner");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void testLoginWithCsrfSucceeds() throws Exception {
        CsrfInfo csrf = getCsrfTokenAndSession();

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("owner");
        request.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                .session(csrf.session)
                .header(csrf.headerName, csrf.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("owner"))
                .andReturn();

        MockHttpSession rotatedSession = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(rotatedSession);
        assertNotEquals(csrf.session.getId(), rotatedSession.getId());
    }

    @Test
    void testLoginFailsWithInvalidCredentials() throws Exception {
        CsrfInfo csrf = getCsrfTokenAndSession();

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("owner");
        request.setPassword("wrong_password");

        mockMvc.perform(post("/api/auth/login")
                .session(csrf.session)
                .header(csrf.headerName, csrf.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void testAuthenticatedWriteWithoutCsrfRejected() throws Exception {
        CsrfInfo csrf = getCsrfTokenAndSession();

        // Login with CSRF to get authenticated session
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest();
        loginRequest.setUsername("owner");
        loginRequest.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .session(csrf.session)
                .header(csrf.headerName, csrf.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        JobApplication app = JobApplication.builder()
                .companyName("Test")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();

        // Perform write operation without CSRF (should be rejected with 403 Forbidden)
        mockMvc.perform(post("/api/applications")
                .session(authenticatedSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(app)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void testAuthenticatedWriteWithCsrfSucceeds() throws Exception {
        CsrfInfo csrf = getCsrfTokenAndSession();

        // Login with CSRF
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest();
        loginRequest.setUsername("owner");
        loginRequest.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .session(csrf.session)
                .header(csrf.headerName, csrf.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Get the rotated session's new CSRF token
        org.springframework.security.web.csrf.CsrfToken newCsrf = (org.springframework.security.web.csrf.CsrfToken) loginResult.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());
        assertNotNull(newCsrf);

        JobApplication app = JobApplication.builder()
                .companyName("Test Corp")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();

        // Perform write operation with CSRF (should succeed)
        mockMvc.perform(post("/api/applications")
                .session(authenticatedSession)
                .header(newCsrf.getHeaderName(), newCsrf.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(app)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Test Corp"));
    }

    @Test
    void testTokenFromAnotherSessionRejected() throws Exception {
        CsrfInfo csrf1 = getCsrfTokenAndSession();
        CsrfInfo csrf2 = getCsrfTokenAndSession();

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("owner");
        request.setPassword("password123");

        // Attempt login using csrf1's session but csrf2's token (should be rejected)
        mockMvc.perform(post("/api/auth/login")
                .session(csrf1.session)
                .header(csrf1.headerName, csrf2.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void testLogoutWithoutCsrfRejected() throws Exception {
        CsrfInfo csrf = getCsrfTokenAndSession();

        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest();
        loginRequest.setUsername("owner");
        loginRequest.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .session(csrf.session)
                .header(csrf.headerName, csrf.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Attempt logout without CSRF (should be rejected)
        mockMvc.perform(post("/api/auth/logout")
                .session(authenticatedSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void testLogoutWithCsrfSucceedsAndInvalidates() throws Exception {
        CsrfInfo csrf = getCsrfTokenAndSession();

        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest();
        loginRequest.setUsername("owner");
        loginRequest.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .session(csrf.session)
                .header(csrf.headerName, csrf.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        org.springframework.security.web.csrf.CsrfToken newCsrf = (org.springframework.security.web.csrf.CsrfToken) loginResult.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());

        // Logout with CSRF (should succeed and invalidate session)
        mockMvc.perform(post("/api/auth/logout")
                .session(authenticatedSession)
                .header(newCsrf.getHeaderName(), newCsrf.getToken()))
                .andExpect(status().isOk());

        assertTrue(authenticatedSession.isInvalid());
    }

    @Test
    void testProtectedEndpointsRejectUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    @WithMockUser(username = "owner")
    void testUserCannotAccessOtherUsersApplication() throws Exception {
        JobApplication otherApp = JobApplication.builder()
                .user(otherUser)
                .companyName("Secrets Corp")
                .jobTitle("Spy")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();
        otherApp = jobApplicationRepository.save(otherApp);

        // GET (no CSRF required, should return 404 Not Found)
        mockMvc.perform(get("/api/applications/" + otherApp.getId()))
                .andExpect(status().isNotFound());

        // PUT (with CSRF, should return 404 Not Found)
        mockMvc.perform(put("/api/applications/" + otherApp.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(otherApp)))
                .andExpect(status().isNotFound());

        // DELETE (with CSRF, should return 404 Not Found)
        mockMvc.perform(delete("/api/applications/" + otherApp.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "owner")
    void testUserCannotAccessOtherUsersDocuments() throws Exception {
        JobApplication otherApp = JobApplication.builder()
                .user(otherUser)
                .companyName("Other Corp")
                .jobTitle("Engineer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();
        otherApp = jobApplicationRepository.save(otherApp);

        ApplicationDocument otherDoc = ApplicationDocument.builder()
                .jobApplication(otherApp)
                .fileName("cv.pdf")
                .fileType("application/pdf")
                .documentType(DocumentType.CV)
                .filePath("uploads/application-" + otherApp.getId() + "/CV_test.pdf")
                .build();
        otherDoc = documentRepository.save(otherDoc);

        // List (should return 404)
        mockMvc.perform(get("/api/applications/" + otherApp.getId() + "/documents"))
                .andExpect(status().isNotFound());

        // Download (should return 404)
        mockMvc.perform(get("/api/applications/" + otherApp.getId() + "/documents/" + otherDoc.getId()))
                .andExpect(status().isNotFound());

        // Delete (with CSRF, should return 404)
        mockMvc.perform(delete("/api/applications/" + otherApp.getId() + "/documents/" + otherDoc.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUploadDocumentWithoutCsrfRejected() throws Exception {
        CsrfInfo csrf = getCsrfTokenAndSession();

        // 1. Login with CSRF to get authenticated session
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest();
        loginRequest.setUsername("owner");
        loginRequest.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .session(csrf.session)
                .header(csrf.headerName, csrf.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);

        // 2. Perform multipart upload without CSRF (should be rejected with 403 CSRF_INVALID)
        org.springframework.mock.web.MockMultipartFile mockFile =
            new org.springframework.mock.web.MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4\n%...".getBytes());

        mockMvc.perform(multipart("/api/applications/1/documents")
                .file(mockFile)
                .param("documentType", "CV")
                .session(authenticatedSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void testDemoAccountWritesWithCsrfStillReturn403() throws Exception {
        CsrfInfo csrf = getCsrfTokenAndSession();

        // Login with demo account
        AuthController.LoginRequest loginRequest = new AuthController.LoginRequest();
        loginRequest.setUsername("demo");
        loginRequest.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .session(csrf.session)
                .header(csrf.headerName, csrf.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession authenticatedSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        org.springframework.security.web.csrf.CsrfToken newCsrf = (org.springframework.security.web.csrf.CsrfToken) loginResult.getRequest().getAttribute(org.springframework.security.web.csrf.CsrfToken.class.getName());

        JobApplication app = JobApplication.builder()
                .companyName("Demo Target")
                .jobTitle("Developer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();

        // Attempt write with correct CSRF (should return 403 Forbidden due to write restrictions on demo accounts)
        mockMvc.perform(post("/api/applications")
                .session(authenticatedSession)
                .header(newCsrf.getHeaderName(), newCsrf.getToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(app)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Demo accounts cannot perform write operations."))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void testAdministratorSeedingConfigurationAndLifecycle() {
        userRepository.findByUsernameIgnoreCase("test_seeder_admin").ifPresent(userRepository::delete);
        long countBefore = userRepository.count();

        com.jobtrack.config.AdminUserSeeder seeder = new com.jobtrack.config.AdminUserSeeder(userRepository, jobApplicationRepository, passwordEncoder, jdbcTemplate);

        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "adminUsername", "test_seeder_admin");
        org.springframework.test.util.ReflectionTestUtils.setField(seeder, "adminPassword", "adminPass123");

        seeder.run();

        assertEquals(countBefore + 1, userRepository.count());
        User seededAdmin = userRepository.findByUsernameIgnoreCase("test_seeder_admin").orElseThrow();
        assertEquals("ROLE_ADMIN", seededAdmin.getRole());
        assertTrue(passwordEncoder.matches("adminPass123", seededAdmin.getPasswordHash()));

        seeder.run();
        assertEquals(countBefore + 1, userRepository.count());

        userRepository.delete(seededAdmin);
    }

    @Test
    void testCorsPreflightWithCsrf() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type, X-CSRF-TOKEN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, org.hamcrest.Matchers.containsString("X-CSRF-TOKEN")));
    }
}
