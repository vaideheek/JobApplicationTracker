package com.jobtrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.dto.*;
import com.jobtrack.entity.*;
import com.jobtrack.enums.*;
import com.jobtrack.repository.*;
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
import java.util.*;
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

    private byte[] createMockZip(Map<String, String> entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                zos.putNextEntry(ze);
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
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

        // Generate 172 applications
        List<String> appIds = new ArrayList<>();
        StringBuilder appsJson = new StringBuilder("[");
        for (int i = 1; i <= 172; i++) {
            String appId = "APP-" + String.format("%03d", i);
            appIds.add(appId);
            String company = (i == 1) ? "Tripadvisor" : ("Company " + i);
            String position = (i == 1) ? "Junior Software Engineer" : ("Position " + i);
            // 57 REJECTED, 115 NO_RESPONSE. App 1 (Tripadvisor) is NO_RESPONSE, apps 2 to 58 are REJECTED, apps 59 to 172 are NO_RESPONSE
            String status = (i >= 2 && i <= 58) ? "REJECTED" : "NO_RESPONSE";

            if (i > 1) appsJson.append(",");
            appsJson.append(String.format(
                "{\"manifestApplicationId\":\"%s\",\"companyName\":\"%s\",\"jobTitle\":\"%s\",\"status\":\"%s\",\"priority\":\"LOW\",\"dateApplied\":\"2026-08-13\",\"stage\":\"Stage\",\"source\":\"Source\",\"notes\":\"Notes\"}",
                appId, company, position, status
            ));
        }
        appsJson.append("]");

        // Generate documents to exactly meet count requirements
        List<String> docJsonList = new ArrayList<>();
        Map<String, String> zip1Entries = new HashMap<>();
        Map<String, String> zip2Entries = new HashMap<>();

        // Add 235 attachments to first 85 applications
        int docIndex = 1;
        for (int i = 0; i < 85; i++) {
            String appId = appIds.get(i);
            int docsForThisApp = (i < 65) ? 3 : 2; // 65 * 3 + 20 * 2 = 195 + 40 = 235 docs
            for (int d = 0; d < docsForThisApp; d++) {
                String docId = "DOC-" + String.format("%03d", docIndex);
                String relativePath = "Folder" + i + "/doc_" + docIndex + ".pdf";
                String filename = "doc_" + docIndex + ".pdf";
                String content = "%PDF-1.4: Content of doc " + docIndex;
                byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
                String sha = getSha256(contentBytes);

                docJsonList.add(String.format(
                    "{\"manifestDocumentId\":\"%s\",\"sourceArchiveId\":\"LAPTOP\",\"relativePath\":\"%s\",\"filename\":\"%s\",\"sizeBytes\":%d,\"sha256\":\"%s\",\"manifestApplicationId\":\"%s\",\"uploadDocumentType\":\"CV\",\"disposition\":\"ATTACH\"}",
                    docId, relativePath, filename, contentBytes.length, sha, appId
                ));
                zip1Entries.put("JobApps/" + relativePath, content);
                docIndex++;
            }
        }

        // Add 69 unassigned unique files
        for (int i = 1; i <= 69; i++) {
            String docId = "DOC-U" + i;
            String relativePath = "Unassigned/unassigned_" + i + ".pdf";
            String filename = "unassigned_" + i + ".pdf";
            String content = "%PDF-1.4: Unassigned content " + i;
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            String sha = getSha256(contentBytes);

            docJsonList.add(String.format(
                "{\"manifestDocumentId\":\"%s\",\"sourceArchiveId\":\"GOOGLE_DRIVE\",\"relativePath\":\"%s\",\"filename\":\"%s\",\"sizeBytes\":%d,\"sha256\":\"%s\",\"manifestApplicationId\":null,\"uploadDocumentType\":\"OTHER\",\"disposition\":\"UNASSIGNED_REVIEW\"}",
                docId, relativePath, filename, contentBytes.length, sha
            ));
            zip2Entries.put("Jobs/" + relativePath, content);
        }

        // Add 16 exact duplicates skipped
        for (int i = 1; i <= 16; i++) {
            String docId = "DOC-D" + i;
            String relativePath = "Duplicates/dup_" + i + ".pdf";
            String filename = "dup_" + i + ".pdf";
            String content = "%PDF-1.4: Content of doc " + i;
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            String sha = getSha256(contentBytes);

            docJsonList.add(String.format(
                "{\"manifestDocumentId\":\"%s\",\"sourceArchiveId\":\"LAPTOP\",\"relativePath\":\"%s\",\"filename\":\"%s\",\"sizeBytes\":%d,\"sha256\":\"%s\",\"manifestApplicationId\":null,\"uploadDocumentType\":\"OTHER\",\"disposition\":\"SKIP_EXACT_DUPLICATE\"}",
                docId, relativePath, filename, contentBytes.length, sha
            ));
            zip1Entries.put("JobApps/" + relativePath, content);
        }

        String docsJson = "[" + String.join(",", docJsonList) + "]";
        String manifestJson = "{\"schemaVersion\":\"1.0\",\"applications\":" + appsJson.toString() + ",\"documents\":" + docsJson + "}";

        MockMultipartFile laptopZip = new MockMultipartFile("laptopZip", "laptop.zip", "application/zip", createMockZip(zip1Entries));
        MockMultipartFile driveZip = new MockMultipartFile("googleDriveZip", "drive.zip", "application/zip", createMockZip(zip2Entries));
        MockMultipartFile manifest = new MockMultipartFile("manifest", "manifest.json", "application/json", manifestJson.getBytes(StandardCharsets.UTF_8));

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

        BulkScanResponse scanResponse = objectMapper.readValue(scanResponseContent, BulkScanResponse.class);
        assertNotNull(scanResponse.getScanId());
        assertEquals(172, scanResponse.getApplications().size());
        assertEquals(69, scanResponse.getUnassignedFiles().size());

        // Verify Tripadvisor duplicate rule matches
        ScannedApplicationGroup tripAdvisorPreview = scanResponse.getApplications().stream()
                .filter(a -> "Tripadvisor".equalsIgnoreCase(a.getCompanyName()))
                .findFirst().orElseThrow();
        assertTrue(tripAdvisorPreview.isDuplicate());
        assertEquals(existingApp.getId(), tripAdvisorPreview.getExistingApplicationId());
        assertEquals(3, tripAdvisorPreview.getDocuments().size());
        assertEquals("VALID", tripAdvisorPreview.getDocuments().get(0).getValidationStatus());

        // Map confirm payload
        List<BulkConfirmApplication> applicationsPayload = new ArrayList<>();
        for (ScannedApplicationGroup app : scanResponse.getApplications()) {
            List<BulkConfirmDocument> docs = new ArrayList<>();
            for (ScannedDocumentPreview doc : app.getDocuments()) {
                docs.add(BulkConfirmDocument.builder()
                        .tempDocId(doc.getTempDocId())
                        .documentType("CV")
                        .build());
            }
            applicationsPayload.add(BulkConfirmApplication.builder()
                    .tempAppId(app.getTempAppId())
                    .companyName(app.getCompanyName())
                    .jobTitle(app.getJobTitle())
                    .status(app.getStatus())
                    .priority(app.getPriority())
                    .dateApplied(app.getDateApplied())
                    .stage(app.getStage())
                    .source(app.getSource())
                    .notes(app.getNotes())
                    .documents(docs)
                    .build());
        }

        BulkConfirmRequest confirmRequest = BulkConfirmRequest.builder()
                .scanId(scanResponse.getScanId())
                .applications(applicationsPayload)
                .build();

        String confirmResponseContent = mockMvc.perform(post("/api/bulk-import/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(confirmRequest))
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        BulkConfirmResponse confirmResponse = objectMapper.readValue(confirmResponseContent, BulkConfirmResponse.class);
        assertEquals(172, confirmResponse.getSuccessCount());
        assertEquals(0, confirmResponse.getFailureCount());

        // Verify Tripadvisor is updated in place
        List<JobApplication> tripAdvisorApps = jobApplicationRepository.findAllByUserId(testUser.getId()).stream()
                .filter(a -> a.getCompanyName().equalsIgnoreCase("Tripadvisor"))
                .toList();
        assertEquals(1, tripAdvisorApps.size());
        JobApplication tripAdvisorResult = tripAdvisorApps.get(0);
        assertEquals(ApplicationStatus.NO_RESPONSE, tripAdvisorResult.getStatus());
        assertEquals(ApplicationPriority.LOW, tripAdvisorResult.getPriority());
        assertNull(tripAdvisorResult.getFollowUpDate());
        assertNull(tripAdvisorResult.getDeadlineDate());
        assertEquals("Stage", tripAdvisorResult.getStage());
        assertEquals("Source", tripAdvisorResult.getSource());
        assertEquals("Notes", tripAdvisorResult.getNotes());

        // Verify batch state is COMPLETED
        ImportBatch batch = importBatchRepository.findById(scanResponse.getScanId()).orElseThrow();
        assertEquals("COMPLETED", batch.getState());
    }

    @Test
    void testDemoAccountIsRestrictedFromImporting() throws Exception {
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

        mockMvc.perform(multipart("/api/bulk-import/scan")
                .file(manifest)
                .file(laptopZip)
                .file(googleDriveZip)
                .session(demoSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testDsStoreAndGenuineDuplicateHashes() throws Exception {
        JobApplication existingTripadvisor = JobApplication.builder()
                .companyName("Tripadvisor")
                .jobTitle("Junior Software Engineer")
                .status(ApplicationStatus.NO_RESPONSE)
                .priority(ApplicationPriority.MEDIUM)
                .user(testUser)
                .build();
        jobApplicationRepository.saveAndFlush(existingTripadvisor);

        List<String> appJsonList = new ArrayList<>();
        List<String> docJsonList = new ArrayList<>();

        List<String> appIds = new ArrayList<>();
        for (int i = 1; i <= 172; i++) {
            String appId = "APP-" + String.format("%03d", i);
            appIds.add(appId);
            String status = (i <= 57) ? "REJECTED" : "NO_RESPONSE";
            String company = (i == 159) ? "Tripadvisor" : "Company_" + i;
            String title = (i == 159) ? "Junior Software Engineer" : "Title_" + i;
            String importAction = (i == 159) ? "MATCH_EXISTING" : "CREATE_OR_MATCH_EXACT";

            appJsonList.add(String.format(
                "{\"manifestApplicationId\":\"%s\",\"companyName\":\"%s\",\"jobTitle\":\"%s\",\"status\":\"%s\",\"priority\":\"MEDIUM\",\"importAction\":\"%s\"}",
                appId, company, title, status, importAction
            ));
        }

        Map<String, String> zip1Entries = new HashMap<>();
        Map<String, String> zip2Entries = new HashMap<>();

        int docIndex = 1;
        for (int i = 0; i < 85; i++) {
            String appId = appIds.get(i);
            int docsForThisApp = (i < 65) ? 3 : 2;
            for (int d = 0; d < docsForThisApp; d++) {
                String docId = "DOC-" + String.format("%03d", docIndex);
                String relativePath = "Folder" + i + "/doc_" + docIndex + ".pdf";
                String filename = "doc_" + docIndex + ".pdf";
                String content = "%PDF-1.4: Content of doc " + docIndex;
                byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
                String sha = getSha256(contentBytes);

                docJsonList.add(String.format(
                    "{\"manifestDocumentId\":\"%s\",\"sourceArchiveId\":\"LAPTOP\",\"relativePath\":\"%s\",\"filename\":\"%s\",\"sizeBytes\":%d,\"sha256\":\"%s\",\"manifestApplicationId\":\"%s\",\"uploadDocumentType\":\"CV\",\"disposition\":\"ATTACH\"}",
                    docId, relativePath, filename, contentBytes.length, sha, appId
                ));
                zip1Entries.put("JobApps/" + relativePath, content);
                docIndex++;
            }
        }

        for (int i = 1; i <= 69; i++) {
            String docId = "DOC-U" + i;
            String relativePath = "Unassigned/unassigned_" + i + ".pdf";
            String filename = "unassigned_" + i + ".pdf";
            String content = "%PDF-1.4: Unassigned content " + i;
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            String sha = getSha256(contentBytes);

            docJsonList.add(String.format(
                "{\"manifestDocumentId\":\"%s\",\"sourceArchiveId\":\"GOOGLE_DRIVE\",\"relativePath\":\"%s\",\"filename\":\"%s\",\"sizeBytes\":%d,\"sha256\":\"%s\",\"manifestApplicationId\":null,\"uploadDocumentType\":\"OTHER\",\"disposition\":\"UNASSIGNED_REVIEW\"}",
                docId, relativePath, filename, contentBytes.length, sha
            ));
            zip2Entries.put("Jobs/" + relativePath, content);
        }

        for (int i = 1; i <= 16; i++) {
            String docId = "DOC-D" + i;
            String relativePath = "Duplicates/dup_" + i + ".pdf";
            String filename = "dup_" + i + ".pdf";
            String content = "%PDF-1.4: Content of doc " + i;
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            String sha = getSha256(contentBytes);

            docJsonList.add(String.format(
                "{\"manifestDocumentId\":\"%s\",\"sourceArchiveId\":\"LAPTOP\",\"relativePath\":\"%s\",\"filename\":\"%s\",\"sizeBytes\":%d,\"sha256\":\"%s\",\"manifestApplicationId\":null,\"uploadDocumentType\":\"OTHER\",\"disposition\":\"SKIP_EXACT_DUPLICATE\"}",
                docId, relativePath, filename, contentBytes.length, sha
            ));
            zip1Entries.put("JobApps/" + relativePath, content);
        }

        zip1Entries.put("JobApps/.DS_Store", "dummy store content");
        zip1Entries.put("JobApps/Folder0/.DS_Store", "dummy store content");
        zip2Entries.put("Jobs/.DS_Store", "dummy store content");

        String manifestJson = String.format(
            "{\"applications\":[%s],\"documents\":[%s]}",
            String.join(",", appJsonList), String.join(",", docJsonList)
        );

        byte[] manifestBytes = manifestJson.getBytes(StandardCharsets.UTF_8);
        byte[] laptopBytes = createMockZip(zip1Entries);
        byte[] driveBytes = createMockZip(zip2Entries);

        MockMultipartFile manifestFile = new MockMultipartFile("manifest", "manifest.json", "application/json", manifestBytes);
        MockMultipartFile laptopZip = new MockMultipartFile("laptopZip", "JobApps.zip", "application/zip", laptopBytes);
        MockMultipartFile googleDriveZip = new MockMultipartFile("googleDriveZip", "Jobs.zip", "application/zip", driveBytes);

        String csrfToken = getCsrfToken(testSession);

        String responseContent = mockMvc.perform(multipart("/api/bulk-import/scan")
                .file(manifestFile)
                .file(laptopZip)
                .file(googleDriveZip)
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        BulkScanResponse scanRes = objectMapper.readValue(responseContent, BulkScanResponse.class);
        assertTrue(scanRes.isCanConfirm());
        assertTrue(scanRes.getValidationIssues().isEmpty());
    }

    @Test
    void testMissingTripadvisor() throws Exception {
        List<String> appJsonList = new ArrayList<>();
        List<String> docJsonList = new ArrayList<>();

        List<String> appIds = new ArrayList<>();
        for (int i = 1; i <= 172; i++) {
            String appId = "APP-" + String.format("%03d", i);
            appIds.add(appId);
            String status = (i <= 57) ? "REJECTED" : "NO_RESPONSE";
            String company = (i == 159) ? "Tripadvisor" : "Company_" + i;
            String title = (i == 159) ? "Junior Software Engineer" : "Title_" + i;
            String importAction = (i == 159) ? "MATCH_EXISTING" : "CREATE_OR_MATCH_EXACT";

            appJsonList.add(String.format(
                "{\"manifestApplicationId\":\"%s\",\"companyName\":\"%s\",\"jobTitle\":\"%s\",\"status\":\"%s\",\"priority\":\"MEDIUM\",\"importAction\":\"%s\"}",
                appId, company, title, status, importAction
            ));
        }

        Map<String, String> zip1Entries = new HashMap<>();
        Map<String, String> zip2Entries = new HashMap<>();

        int docIndex = 1;
        for (int i = 0; i < 85; i++) {
            String appId = appIds.get(i);
            int docsForThisApp = (i < 65) ? 3 : 2;
            for (int d = 0; d < docsForThisApp; d++) {
                String docId = "DOC-" + String.format("%03d", docIndex);
                String relativePath = "Folder" + i + "/doc_" + docIndex + ".pdf";
                String filename = "doc_" + docIndex + ".pdf";
                String content = "%PDF-1.4: Content of doc " + docIndex;
                byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
                String sha = getSha256(contentBytes);

                docJsonList.add(String.format(
                    "{\"manifestDocumentId\":\"%s\",\"sourceArchiveId\":\"LAPTOP\",\"relativePath\":\"%s\",\"filename\":\"%s\",\"sizeBytes\":%d,\"sha256\":\"%s\",\"manifestApplicationId\":\"%s\",\"uploadDocumentType\":\"CV\",\"disposition\":\"ATTACH\"}",
                    docId, relativePath, filename, contentBytes.length, sha, appId
                ));
                zip1Entries.put("JobApps/" + relativePath, content);
                docIndex++;
            }
        }

        for (int i = 1; i <= 69; i++) {
            String docId = "DOC-U" + i;
            String relativePath = "Unassigned/unassigned_" + i + ".pdf";
            String filename = "unassigned_" + i + ".pdf";
            String content = "%PDF-1.4: Unassigned content " + i;
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            String sha = getSha256(contentBytes);

            docJsonList.add(String.format(
                "{\"manifestDocumentId\":\"%s\",\"sourceArchiveId\":\"GOOGLE_DRIVE\",\"relativePath\":\"%s\",\"filename\":\"%s\",\"sizeBytes\":%d,\"sha256\":\"%s\",\"manifestApplicationId\":null,\"uploadDocumentType\":\"OTHER\",\"disposition\":\"UNASSIGNED_REVIEW\"}",
                docId, relativePath, filename, contentBytes.length, sha
            ));
            zip2Entries.put("Jobs/" + relativePath, content);
        }

        for (int i = 1; i <= 16; i++) {
            String docId = "DOC-D" + i;
            String relativePath = "Duplicates/dup_" + i + ".pdf";
            String filename = "dup_" + i + ".pdf";
            String content = "%PDF-1.4: Content of doc " + i;
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            String sha = getSha256(contentBytes);

            docJsonList.add(String.format(
                "{\"manifestDocumentId\":\"%s\",\"sourceArchiveId\":\"LAPTOP\",\"relativePath\":\"%s\",\"filename\":\"%s\",\"sizeBytes\":%d,\"sha256\":\"%s\",\"manifestApplicationId\":null,\"uploadDocumentType\":\"OTHER\",\"disposition\":\"SKIP_EXACT_DUPLICATE\"}",
                docId, relativePath, filename, contentBytes.length, sha
            ));
            zip1Entries.put("JobApps/" + relativePath, content);
        }

        String manifestJson = String.format(
            "{\"applications\":[%s],\"documents\":[%s]}",
            String.join(",", appJsonList), String.join(",", docJsonList)
        );

        byte[] manifestBytes = manifestJson.getBytes(StandardCharsets.UTF_8);
        byte[] laptopBytes = createMockZip(zip1Entries);
        byte[] driveBytes = createMockZip(zip2Entries);

        MockMultipartFile manifestFile = new MockMultipartFile("manifest", "manifest.json", "application/json", manifestBytes);
        MockMultipartFile laptopZip = new MockMultipartFile("laptopZip", "JobApps.zip", "application/zip", laptopBytes);
        MockMultipartFile googleDriveZip = new MockMultipartFile("googleDriveZip", "Jobs.zip", "application/zip", driveBytes);

        String csrfToken = getCsrfToken(testSession);

        mockMvc.perform(multipart("/api/bulk-import/scan")
                .file(manifestFile)
                .file(laptopZip)
                .file(googleDriveZip)
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isInternalServerError());
    }

    private String getCsrfToken(MockHttpSession session) throws Exception {
        String response = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
