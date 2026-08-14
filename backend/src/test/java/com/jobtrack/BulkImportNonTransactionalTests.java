package com.jobtrack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.dto.*;
import com.jobtrack.entity.*;
import com.jobtrack.enums.*;
import com.jobtrack.exception.*;
import com.jobtrack.repository.*;
import com.jobtrack.service.*;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
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
public class BulkImportNonTransactionalTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationDocumentRepository documentRepository;

    @Autowired
    private ImportBatchRepository importBatchRepository;

    @Autowired
    private BulkImportService bulkImportService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DocumentStorageService storageService;

    private User testUser;
    private MockHttpSession testSession;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        jobApplicationRepository.deleteAll();
        importBatchRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .username("test_user_non_tx")
                .passwordHash(passwordEncoder.encode("password123"))
                .displayName("Test User")
                .role("ROLE_USER")
                .enabled(true)
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

    @Test
    void testSimultaneousConfirmation() throws Exception {
        BulkScanResponse scanRes = doScan();

        BulkConfirmApplication appConfirm = new BulkConfirmApplication();
        appConfirm.setCompanyName("Company_159");
        appConfirm.setJobTitle("Title_159");
        appConfirm.setStatus("NO_RESPONSE");
        appConfirm.setPriority("MEDIUM");
        appConfirm.setDocuments(Collections.emptyList());

        BulkConfirmRequest request = new BulkConfirmRequest();
        request.setScanId(scanRes.getScanId());
        request.setApplications(List.of(appConfirm));

        String jsonPayload = objectMapper.writeValueAsString(request);
        String csrfToken = getCsrfToken(testSession);

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Future<Integer>[] futures = new java.util.concurrent.Future[2];

        for (int i = 0; i < 2; i++) {
            futures[i] = executor.submit(() -> {
                try {
                    return mockMvc.perform(post("/api/bulk-import/confirm")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(jsonPayload)
                            .session(testSession)
                            .header("X-CSRF-TOKEN", csrfToken))
                            .andReturn().getResponse().getStatus();
                } catch (Exception e) {
                    return 500;
                }
            });
        }

        executor.shutdown();
        int status1 = futures[0].get();
        int status2 = futures[1].get();

        assertTrue((status1 == 200 && status2 == 409) || (status1 == 409 && status2 == 200),
                "One confirm must succeed with 200 and the other must fail with 409 Conflict. Got: " + status1 + " and " + status2);
    }

    @Test
    void testConfirmIdempotencyCompleted() throws Exception {
        BulkScanResponse scanRes = doScan();

        BulkConfirmApplication appConfirm = new BulkConfirmApplication();
        appConfirm.setCompanyName("Company_159");
        appConfirm.setJobTitle("Title_159");
        appConfirm.setStatus("NO_RESPONSE");
        appConfirm.setPriority("MEDIUM");
        appConfirm.setDocuments(Collections.emptyList());

        BulkConfirmRequest request = new BulkConfirmRequest();
        request.setScanId(scanRes.getScanId());
        request.setApplications(List.of(appConfirm));

        String jsonPayload = objectMapper.writeValueAsString(request);
        String csrfToken = getCsrfToken(testSession);

        mockMvc.perform(post("/api/bulk-import/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bulk-import/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isConflict());
    }

    @Test
    void testDatabaseRollbackAndStorageCompensation() throws Exception {
        BulkScanResponse scanRes = doScan();

        ScannedApplicationGroup appWithDoc = scanRes.getApplications().stream()
                .filter(a -> !a.getDocuments().isEmpty())
                .findFirst().orElseThrow();

        String tempDocId = appWithDoc.getDocuments().get(0).getTempDocId();

        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "jobtrack-import-" + testUser.getId() + "-" + scanRes.getScanId());
        Path mappingsPath = tempDir.resolve("mappings.json");
        com.fasterxml.jackson.databind.JsonNode mappingsNode = objectMapper.readTree(Files.readAllBytes(mappingsPath));
        Map<String, String> docIdToSha = objectMapper.convertValue(mappingsNode.get("docIdToSha"), Map.class);
        Map<String, String> shaToTempPath = objectMapper.convertValue(mappingsNode.get("shaToTempPath"), Map.class);

        String sha = docIdToSha.get(tempDocId);
        String tempFilePathStr = shaToTempPath.get(sha);

        Files.deleteIfExists(Paths.get(tempFilePathStr));

        BulkConfirmApplication appConfirm = new BulkConfirmApplication();
        appConfirm.setCompanyName(appWithDoc.getCompanyName());
        appConfirm.setJobTitle(appWithDoc.getJobTitle());
        appConfirm.setStatus(appWithDoc.getStatus());
        appConfirm.setPriority(appWithDoc.getPriority());
        
        BulkConfirmDocument docConfirm = new BulkConfirmDocument();
        docConfirm.setTempDocId(tempDocId);
        docConfirm.setDocumentType("CV");
        appConfirm.setDocuments(List.of(docConfirm));

        BulkConfirmRequest request = new BulkConfirmRequest();
        request.setScanId(scanRes.getScanId());
        request.setApplications(List.of(appConfirm));

        String jsonPayload = objectMapper.writeValueAsString(request);
        String csrfToken = getCsrfToken(testSession);

        mockMvc.perform(post("/api/bulk-import/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isInternalServerError());

        long count = jobApplicationRepository.count();
        assertEquals(1, count, "All database writes must be rolled back on confirmation failure, leaving only the seeded record.");

        ImportBatch batch = importBatchRepository.findById(scanRes.getScanId()).orElseThrow();
        assertEquals("FAILED", batch.getState());
    }

    @Test
    void testPreservationOfOldDocuments() throws Exception {
        JobApplication existingApp = JobApplication.builder()
                .companyName("Tripadvisor")
                .jobTitle("Junior Software Engineer")
                .status(ApplicationStatus.NO_RESPONSE)
                .priority(ApplicationPriority.MEDIUM)
                .user(testUser)
                .build();
        existingApp = jobApplicationRepository.saveAndFlush(existingApp);

        String actualStoredPath = storageService.store(existingApp.getId(), 
                new CustomMultipartFile("%PDF-1.4: old doc content".getBytes(), "file", "old_doc.pdf", "application/pdf"), 
                DocumentType.CV, "old_doc.pdf");

        ApplicationDocument oldDoc = ApplicationDocument.builder()
                .jobApplication(existingApp)
                .fileName("old_doc.pdf")
                .fileType("application/pdf")
                .documentType(DocumentType.CV)
                .filePath(actualStoredPath)
                .uploadedAt(LocalDateTime.now())
                .build();
        oldDoc = documentRepository.saveAndFlush(oldDoc);

        assertTrue(storageExists(actualStoredPath), "Replaced old file must initially exist in storage.");

        BulkScanResponse scanRes = doScan();

        ScannedApplicationGroup tripApp = scanRes.getApplications().stream()
                .filter(a -> "Tripadvisor".equals(a.getCompanyName()))
                .findFirst().orElseThrow();
        String tempDocId = tripApp.getDocuments().get(0).getTempDocId();

        BulkConfirmApplication confirmApp1 = new BulkConfirmApplication();
        confirmApp1.setCompanyName("Tripadvisor");
        confirmApp1.setJobTitle("Junior Software Engineer");
        confirmApp1.setStatus("NO_RESPONSE");
        confirmApp1.setPriority("MEDIUM");
        BulkConfirmDocument docConfirm1 = new BulkConfirmDocument();
        docConfirm1.setTempDocId(tempDocId);
        docConfirm1.setDocumentType("CV");
        confirmApp1.setDocuments(List.of(docConfirm1));

        ScannedApplicationGroup failingApp = scanRes.getApplications().stream()
                .filter(a -> !a.getCompanyName().equals("Tripadvisor") && !a.getDocuments().isEmpty())
                .findFirst().orElseThrow();
        String failingTempDocId = failingApp.getDocuments().get(0).getTempDocId();

        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "jobtrack-import-" + testUser.getId() + "-" + scanRes.getScanId());
        Path mappingsPath = tempDir.resolve("mappings.json");
        com.fasterxml.jackson.databind.JsonNode mappingsNode = objectMapper.readTree(Files.readAllBytes(mappingsPath));
        Map<String, String> docIdToSha = objectMapper.convertValue(mappingsNode.get("docIdToSha"), Map.class);
        Map<String, String> shaToTempPath = objectMapper.convertValue(mappingsNode.get("shaToTempPath"), Map.class);
        String sha = docIdToSha.get(failingTempDocId);
        String tempFilePathStr = shaToTempPath.get(sha);
        Files.deleteIfExists(Paths.get(tempFilePathStr));

        BulkConfirmApplication confirmApp2 = new BulkConfirmApplication();
        confirmApp2.setCompanyName(failingApp.getCompanyName());
        confirmApp2.setJobTitle(failingApp.getJobTitle());
        confirmApp2.setStatus(failingApp.getStatus());
        confirmApp2.setPriority(failingApp.getPriority());
        BulkConfirmDocument docConfirm2 = new BulkConfirmDocument();
        docConfirm2.setTempDocId(failingTempDocId);
        docConfirm2.setDocumentType("CV");
        confirmApp2.setDocuments(List.of(docConfirm2));

        BulkConfirmRequest request = new BulkConfirmRequest();
        request.setScanId(scanRes.getScanId());
        request.setApplications(List.of(confirmApp1, confirmApp2));

        String jsonPayload = objectMapper.writeValueAsString(request);
        String csrfToken = getCsrfToken(testSession);

        mockMvc.perform(post("/api/bulk-import/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isInternalServerError());

        assertTrue(documentRepository.existsById(oldDoc.getId()), "Old document DB record must still exist after rollback.");
        assertTrue(storageExists(actualStoredPath), "Replaced old file must NOT be deleted from storage if transaction fails.");
    }

    @Test
    void testConfirmRetry() throws Exception {
        BulkScanResponse scanRes = doScan();

        ImportBatch batch = importBatchRepository.findById(scanRes.getScanId()).orElseThrow();
        batch.setState("FAILED");
        importBatchRepository.saveAndFlush(batch);

        BulkConfirmApplication appConfirm = new BulkConfirmApplication();
        appConfirm.setCompanyName("Company_159");
        appConfirm.setJobTitle("Title_159");
        appConfirm.setStatus("NO_RESPONSE");
        appConfirm.setPriority("MEDIUM");
        appConfirm.setDocuments(Collections.emptyList());

        BulkConfirmRequest request = new BulkConfirmRequest();
        request.setScanId(scanRes.getScanId());
        request.setApplications(List.of(appConfirm));

        String jsonPayload = objectMapper.writeValueAsString(request);
        String csrfToken = getCsrfToken(testSession);

        mockMvc.perform(post("/api/bulk-import/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk());
    }

    private boolean storageExists(String path) {
        try (InputStream is = storageService.loadAsStream(path)) {
            return is != null;
        } catch (Exception e) {
            return false;
        }
    }

    private BulkScanResponse doScan() throws Exception {
        if (jobApplicationRepository.findByCompanyNameIgnoreCaseAndJobTitleIgnoreCaseAndUserId("Tripadvisor", "Junior Software Engineer", testUser.getId()).isEmpty()) {
            JobApplication existingTripadvisor = JobApplication.builder()
                    .companyName("Tripadvisor")
                    .jobTitle("Junior Software Engineer")
                    .status(ApplicationStatus.NO_RESPONSE)
                    .priority(ApplicationPriority.MEDIUM)
                    .user(testUser)
                    .build();
            jobApplicationRepository.saveAndFlush(existingTripadvisor);
        }

        List<String> appJsonList = new ArrayList<>();
        List<String> docJsonList = new ArrayList<>();

        List<String> appIds = new ArrayList<>();
        for (int i = 1; i <= 172; i++) {
            String appId = "APP-" + String.format("%03d", i);
            appIds.add(appId);
            String status = (i <= 57) ? "REJECTED" : "NO_RESPONSE";
            String company = (i == 1) ? "Tripadvisor" : "Company_" + i;
            String title = (i == 1) ? "Junior Software Engineer" : "Title_" + i;
            String importAction = (i == 1) ? "MATCH_EXISTING" : "CREATE_OR_MATCH_EXACT";

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
            String content = "%PDF-1.4: Duplicate content " + i;
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

        String responseContent = mockMvc.perform(multipart("/api/bulk-import/scan")
                .file(manifestFile)
                .file(laptopZip)
                .file(googleDriveZip)
                .session(testSession)
                .header("X-CSRF-TOKEN", csrfToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readValue(responseContent, BulkScanResponse.class);
    }

    private byte[] createMockZip(Map<String, String> entries) throws IOException {
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

    private String getSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 digest failed", e);
        }
    }

    private String getCsrfToken(MockHttpSession session) throws Exception {
        String response = mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
