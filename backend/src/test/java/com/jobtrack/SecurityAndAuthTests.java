package com.jobtrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.controller.AuthController;
import com.jobtrack.entity.AppUser;
import com.jobtrack.repository.AppUserRepository;
import com.jobtrack.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        AppUser admin = AppUser.builder()
                .email("admin@test.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role("ROLE_ADMIN")
                .enabled(true)
                .build();
        userRepository.save(admin);
    }

    @Test
    void testSuccessfulLogin() throws Exception {
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.email").value("admin@test.com"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    @Test
    void testIncorrectPassword() throws Exception {
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("wrong_password");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void testDisabledUser() throws Exception {
        AppUser admin = userRepository.findByEmailIgnoreCase("admin@test.com").orElseThrow();
        admin.setEnabled(false);
        userRepository.save(admin);

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testMissingToken() throws Exception {
        mockMvc.perform(get("/api/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testValidToken() throws Exception {
        String token = jwtService.generateToken("admin@test.com");

        mockMvc.perform(get("/api/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void testMalformedToken() throws Exception {
        mockMvc.perform(get("/api/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer malformedTokenString"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testInvalidJwtSignature() throws Exception {
        String token = jwtService.generateToken("admin@test.com");
        String tamperedToken = token + "tampered";

        mockMvc.perform(get("/api/applications")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testAuthMeEndpoint() throws Exception {
        String token = jwtService.generateToken("admin@test.com");

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("admin@test.com"))
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"));
    }

    @Test
    void testAdministratorSeedingDoesNotDuplicate() {
        long countBefore = userRepository.count();
        assertTrue(countBefore > 0);

        // Execute CommandLineRunner seeder explicitly
        com.jobtrack.config.AdminUserSeeder seeder = new com.jobtrack.config.AdminUserSeeder(userRepository, passwordEncoder);
        seeder.run();
        
        assertEquals(countBefore, userRepository.count());
    }

    @Test
    void testCorsOriginParsing() throws Exception {
        mockMvc.perform(options("/api/applications")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
