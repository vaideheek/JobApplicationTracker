package com.jobtrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.dto.BulkConfirmApplication;
import com.jobtrack.dto.BulkConfirmDocument;
import com.jobtrack.dto.BulkConfirmRequest;
import com.jobtrack.dto.BulkConfirmResponse;
import com.jobtrack.entity.ImportBatch;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.repository.ImportBatchRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.service.BulkImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BulkImportTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private BulkImportService bulkImportService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private MockHttpSession testSession;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        jobApplicationRepository.deleteAll();
        importBatchRepository.deleteAll();

        // Create standard test user
        testUser = User.builder()
                .username("import_tester")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("Import Tester")
                .role("ROLE_USER")
                .enabled(true)
                .demoAccount(false)
                .build();
        testUser = userRepository.saveAndFlush(testUser);

        testSession = new MockHttpSession();
        testSession.setAttribute("SPRING_SECURITY_CONTEXT",
                new org.springframework.security.core.context.SecurityContextImpl(
                        new org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken(
                                testUser.getUsername(), "password123",
                                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                        )
                ));
    }

    private byte[] createMockZip(String filename, String content) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(filename);
            zos.putNextEntry(entry);
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private String getSha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Test
    void testBulkScanAndConfirmWorkflow() throws Exception {
        // Create an existing database record to verify duplicate matching rules
        JobApplication existingApp = JobApplication.builder()
                .user(testUser)
                .companyName("Tripadvisor")
                .jobTitle("Junior Software Engineer")
                .status(ApplicationStatus.APPLIED)
                .priority(ApplicationPriority.MEDIUM)
                .build();
        jobApplicationRepository.saveAndFlush(existingApp);

        // Prepare synthetic mock data
        String testDocContent = "This is a CV document content for test.";
        byte[] docBytes = testDocContent.getBytes(StandardCharsets.UTF_8);
        String docSha = getSha256(docBytes);
        int docSize = docBytes.length;

        // ZIP 1 containing CV
        byte[] zip1Bytes = createMockZip("CV_Tripadvisor.pdf", testDocContent);
        MockMultipartFile laptopZip = new MockMultipartFile("laptopZip", "laptop.zip", "application/zip", zip1Bytes);

        // ZIP 2 containing unassigned file
        String unassignedContent = "This is an unassigned CV document.";
        byte[] zip2Bytes = createMockZip("extra_file.pdf", unassignedContent);
        MockMultipartFile driveZip = new MockMultipartFile("googleDriveZip", "drive.zip", "application/zip", zip2Bytes);

        // JSON Manifest referencing 2 applications (Tripadvisor which reuses existing and Google which is new)
        String manifestJson = "["
                + "  {"
                + "    \"company\": \"Tripadvisor\","
                + "    \"position\": \"Junior Software Engineer\","
                + "    \"status\": \"NO_RESPONSE\","
                + "    \"priority\": \"LOW\","
                + "    \"dateApplied\": \"2026-08-13\","
                + "    \"files\": ["
                + "      {"
                + "        \"name\": \"CV_Tripadvisor.pdf\","
                + "        \"type\": \"CV\","
                + "        \"size\": " + docSize + ","
                + "        \"sha256\": \"" + docSha + "\""
                + "      }"
                + "    ]"
                + "  },"
                + "  {"
                + "    \"company\": \"Google\","
                + "    \"position\": \"Staff Engineer\","
                + "    \"status\": \"REJECTED\","
                + "    \"priority\": \"LOW\","
                + "    \"dateApplied\": \"2026-08-12\","
                + "    \"files\": []"
                + "  }"
                + "]";
        MockMultipartFile manifest = new MockMultipartFile("manifest", "manifest.json", "application/json", manifestJson.getBytes(StandardCharsets.UTF_8));

        // Get CSRF Token
        String csrfToken = getCsrfToken(testSession);

        // Perform Scan
        String scanResponseContent = mockMvc.perform(multipart("/api/bulk-import/scan")
                .file(manifest)
                .file(laptopZip)
                .file(driveZip)
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.jobtrack.dto.BulkScanResponse scanResponse = objectMapper.readValue(scanResponseContent, com.jobtrack.dto.BulkScanResponse.class);
        assertNotNull(scanResponse.getScanId());
        assertEquals(2, scanResponse.getApplications().size());
        assertEquals(1, scanResponse.getUnassignedFiles().size());

        // Verify Tripadvisor is marked duplicate / matches existing
        com.jobtrack.dto.ScannedApplicationGroup tripAdvisorPreview = scanResponse.getApplications().stream()
                .filter(a -> "Tripadvisor".equalsIgnoreCase(a.getCompanyName()))
                .findFirst().orElseThrow();
        assertTrue(tripAdvisorPreview.isDuplicate());
        assertEquals(existingApp.getId(), tripAdvisorPreview.getExistingApplicationId());
        assertEquals("VALID", tripAdvisorPreview.getDocuments().get(0).getValidationStatus());

        // Verify Google is marked new (not duplicate)
        com.jobtrack.dto.ScannedApplicationGroup googlePreview = scanResponse.getApplications().stream()
                .filter(a -> "Google".equalsIgnoreCase(a.getCompanyName()))
                .findFirst().orElseThrow();
        assertFalse(googlePreview.isDuplicate());

        // Confirm Scan
        BulkConfirmRequest confirmRequest = BulkConfirmRequest.builder()
                .scanId(scanResponse.getScanId())
                .applications(List.of(
                        BulkConfirmApplication.builder()
                                .tempAppId(tripAdvisorPreview.getTempAppId())
                                .companyName(tripAdvisorPreview.getCompanyName())
                                .jobTitle(tripAdvisorPreview.getJobTitle())
                                .status(tripAdvisorPreview.getStatus())
                                .priority(tripAdvisorPreview.getPriority())
                                .dateApplied(tripAdvisorPreview.getDateApplied())
                                .documents(List.of(
                                        BulkConfirmDocument.builder()
                                                .tempDocId(tripAdvisorPreview.getDocuments().get(0).getTempDocId())
                                                .documentType("CV")
                                                .build()
                                ))
                                .build(),
                        BulkConfirmApplication.builder()
                                .tempAppId(googlePreview.getTempAppId())
                                .companyName(googlePreview.getCompanyName())
                                .jobTitle(googlePreview.getJobTitle())
                                .status(googlePreview.getStatus())
                                .priority(googlePreview.getPriority())
                                .dateApplied(googlePreview.getDateApplied())
                                .documents(Collections.emptyList())
                                .build()
                ))
                .build();

        String confirmResponseContent = mockMvc.perform(post("/api/bulk-import/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmRequest))
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        BulkConfirmResponse confirmResponse = objectMapper.readValue(confirmResponseContent, BulkConfirmResponse.class);
        assertEquals(2, confirmResponse.getSuccessCount());
        assertEquals(0, confirmResponse.getFailureCount());

        // Verify Tripadvisor was updated in place (NO duplicate record created, status is now NO_RESPONSE, priority is LOW, date cleanups applied)
        List<JobApplication> tripAdvisorApps = jobApplicationRepository.findAllByUserId(testUser.getId()).stream()
                .filter(a -> a.getCompanyName().equalsIgnoreCase("Tripadvisor"))
                .toList();
        assertEquals(1, tripAdvisorApps.size());
        JobApplication tripAdvisorResult = tripAdvisorApps.get(0);
        assertEquals(ApplicationStatus.NO_RESPONSE, tripAdvisorResult.getStatus());
        assertEquals(ApplicationPriority.LOW, tripAdvisorResult.getPriority());
        assertNull(tripAdvisorResult.getFollowUpDate());
        assertNull(tripAdvisorResult.getDeadlineDate());

        // Verify Google is created
        List<JobApplication> googleApps = jobApplicationRepository.findAllByUserId(testUser.getId()).stream()
                .filter(a -> a.getCompanyName().equalsIgnoreCase("Google"))
                .toList();
        assertEquals(1, googleApps.size());
        JobApplication googleResult = googleApps.get(0);
        assertEquals(ApplicationStatus.REJECTED, googleResult.getStatus());
        assertNull(googleResult.getFollowUpDate());
        assertNull(googleResult.getDeadlineDate());

        // Verify Idempotency constraint is stored in import_batches
        ImportBatch batch = importBatchRepository.findById(scanResponse.getScanId()).orElseThrow();
        assertEquals("COMPLETED", batch.getState());
        assertEquals(testUser.getId(), batch.getUser().getId());

        // Attempting to scan the same manifest file again should fail to protect against double imports
        mockMvc.perform(multipart("/api/bulk-import/scan")
                .file(manifest)
                .file(laptopZip)
                .file(driveZip)
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void testDemoAccountIsRestrictedFromImporting() throws Exception {
        // Create demo account
        User demoUser = User.builder()
                .username("demo_tester")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("Demo Tester")
                .role("ROLE_USER")
                .enabled(true)
                .demoAccount(true)
                .build();
        demoUser = userRepository.saveAndFlush(demoUser);

        MockHttpSession demoSession = new MockHttpSession();
        demoSession.setAttribute("SPRING_SECURITY_CONTEXT",
                new org.springframework.security.core.context.SecurityContextImpl(
                        new org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken(
                                demoUser.getUsername(), "password123",
                                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                        )
                ));

        String csrfToken = getCsrfToken(demoSession);

        MockMultipartFile manifest = new MockMultipartFile("manifest", "manifest.json", "application/json", new byte[0]);
        MockMultipartFile laptopZip = new MockMultipartFile("laptopZip", "laptop.zip", "application/zip", new byte[0]);
        MockMultipartFile googleDriveZip = new MockMultipartFile("googleDriveZip", "drive.zip", "application/zip", new byte[0]);

        // Scan should return 403 Forbidden for demo account
        mockMvc.perform(multipart("/api/bulk-import/scan")
                .file(manifest)
                .file(laptopZip)
                .file(googleDriveZip)
                .session(demoSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isForbidden());
    }

    private String getCsrfToken(MockHttpSession session) throws Exception {
        String response = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
