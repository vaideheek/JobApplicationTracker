package com.jobtrack;

import com.jobtrack.entity.ApplicationDocument;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.User;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.enums.DocumentType;
import com.jobtrack.repository.ApplicationDocumentRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.service.R2StorageService;
import com.jobtrack.util.FileValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class DocumentStorageAndValidationTests {

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

    private JobApplication testApp;
    private User testUser;

    @BeforeEach
    void setUp() {
        documentRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();

        // Seed test user "owner"
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

        JobApplication app = JobApplication.builder()
                .user(testUser)
                .companyName("Test Corp")
                .jobTitle("Engineer")
                .status(ApplicationStatus.APPLIED)
                .dateApplied(LocalDate.now())
                .build();
        testApp = applicationRepository.save(app);
    }

    @Test
    void testEmptyUploadRejection() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> FileValidator.validateFile(emptyFile));
    }

    @Test
    void testOversizedUploadRejection() {
        byte[] largeBytes = new byte[11 * 1024 * 1024]; // 11MB
        MockMultipartFile largeFile = new MockMultipartFile("file", "test.pdf", "application/pdf", largeBytes);
        assertThrows(IllegalArgumentException.class, () -> FileValidator.validateFile(largeFile));
    }

    @Test
    void testFakePdfRejection() {
        byte[] badBytes = "NOTAPDFFILECONTENT".getBytes();
        MockMultipartFile fakePdf = new MockMultipartFile("file", "test.pdf", "application/pdf", badBytes);
        assertThrows(IllegalArgumentException.class, () -> FileValidator.validateFile(fakePdf));
    }

    @Test
    void testFakeDocxRejection() {
        byte[] badBytes = "NOTAZIPFILECONTENT".getBytes();
        MockMultipartFile fakeDocx = new MockMultipartFile("file", "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", badBytes);
        assertThrows(IllegalArgumentException.class, () -> FileValidator.validateFile(fakeDocx));
    }

    @Test
    void testValidPdfAcceptance() {
        byte[] validPdfBytes = "%PDF-1.4\n%...".getBytes();
        MockMultipartFile validPdf = new MockMultipartFile("file", "test.pdf", "application/pdf", validPdfBytes);
        assertDoesNotThrow(() -> FileValidator.validateFile(validPdf));
    }

    @Test
    void testValidDocxAcceptance() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zos.write("<xml></xml>".getBytes());
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write("<xml></xml>".getBytes());
            zos.closeEntry();
        }

        byte[] validDocxBytes = baos.toByteArray();
        MockMultipartFile validDocx = new MockMultipartFile("file", "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", validDocxBytes);
        assertDoesNotThrow(() -> FileValidator.validateFile(validDocx));
    }

    @Test
    void testR2StorageServiceStore() throws IOException {
        S3Client mockS3Client = Mockito.mock(S3Client.class);
        when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        R2StorageService r2Service = new R2StorageService(mockS3Client, "test-bucket");
        MultipartFile mockFile = new MockMultipartFile("file", "test.pdf", "application/pdf", "%PDF-1.4\n%...".getBytes());

        String key = r2Service.store(1L, mockFile, DocumentType.CV, "CV_test.pdf");
        assertNotNull(key);
        assertTrue(key.contains("application-1/CV_test.pdf"));

        verify(mockS3Client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @WithMockUser(username = "owner")
    void testDocumentResponseDtoDoesNotExposeFilePath() throws Exception {
        ApplicationDocument doc = ApplicationDocument.builder()
                .jobApplication(testApp)
                .fileName("test.pdf")
                .fileType("application/pdf")
                .documentType(DocumentType.CV)
                .filePath("/secret/absolute/local/path/to/test.pdf")
                .build();
        documentRepository.save(doc);

        mockMvc.perform(get("/api/applications/" + testApp.getId() + "/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("test.pdf"))
                .andExpect(jsonPath("$[0].filePath").doesNotExist())
                .andExpect(jsonPath("$[0].jobApplication").doesNotExist());
    }
}
