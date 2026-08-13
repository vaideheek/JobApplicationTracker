package com.jobtrack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobtrack.dto.*;
import com.jobtrack.entity.*;
import com.jobtrack.enums.*;
import com.jobtrack.exception.*;
import com.jobtrack.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BulkImportService {

    private final UserRepository userRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final ImportBatchRepository importBatchRepository;
    private final ApplicationDocumentService documentService;
    private final DocumentStorageService storageService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    private static final long MAX_ZIP_EXPANDED_SIZE = 200 * 1024 * 1024; // 200 MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int MAX_RAW_ENTRIES = 1000;
    private static final int MAX_SUPPORTED_DOCUMENTS = 400;

    public BulkScanResponse scan(MultipartFile manifestFile, MultipartFile laptopZip, MultipartFile googleDriveZip) {
        currentUserService.verifyNotDemo();
        User currentUser = currentUserService.getCurrentUser();

        String scanId = UUID.randomUUID().toString();
        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "jobtrack-import-" + currentUser.getId() + "-" + scanId);

        try {
            Files.createDirectories(tempDir);
            Path filesDir = tempDir.resolve("files");
            Files.createDirectories(filesDir);

            // Compute manifest hash
            byte[] manifestBytes = manifestFile.getBytes();
            String manifestHash = getSha256(manifestBytes);

            // Check if this manifest has already been successfully imported
            Optional<ImportBatch> completedBatch = importBatchRepository.findByUserIdAndManifestHashAndState(
                    currentUser.getId(), manifestHash, "COMPLETED"
            );
            if (completedBatch.isPresent()) {
                throw new IllegalStateException("This manifest has already been successfully imported under batch: " + completedBatch.get().getId());
            }

            // State mappings tracking
            Map<String, String> shaToTempPath = new HashMap<>(); // unique files
            Map<String, String> origNameToSha = new HashMap<>(); // file path in zip to SHA
            Map<String, Long> origNameToSize = new HashMap<>();

            int totalRawEntries = 0;
            long totalUncompressedBytes = 0;
            int totalExtractedDocs = 0;
            int skippedDuplicatesCount = 0;

            MultipartFile[] zipFiles = { laptopZip, googleDriveZip };

            for (MultipartFile zipFile : zipFiles) {
                if (zipFile == null || zipFile.isEmpty()) {
                    continue;
                }
                try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        totalRawEntries++;
                        if (totalRawEntries > MAX_RAW_ENTRIES) {
                            throw new IllegalArgumentException("ZIP contains too many raw entries. Maximum allowed is " + MAX_RAW_ENTRIES);
                        }

                        String name = entry.getName();

                        // ZIP-slip prevention
                        Path targetPath = tempDir.resolve(name).normalize();
                        if (!targetPath.startsWith(tempDir)) {
                            throw new SecurityException("ZIP slip attempt detected: " + name);
                        }

                        // Ignore directories and system metadata
                        if (entry.isDirectory() || name.contains("__MACOSX") || Paths.get(name).getFileName().toString().startsWith("._")) {
                            continue;
                        }

                        totalExtractedDocs++;
                        if (totalExtractedDocs > MAX_SUPPORTED_DOCUMENTS) {
                            throw new IllegalArgumentException("ZIP contains too many supported documents. Maximum allowed is " + MAX_SUPPORTED_DOCUMENTS);
                        }

                        // Stream bytes and validate size
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long fileBytesCount = 0;
                        while ((bytesRead = zis.read(buffer)) != -1) {
                            fileBytesCount += bytesRead;
                            totalUncompressedBytes += bytesRead;
                            if (fileBytesCount > MAX_FILE_SIZE) {
                                throw new IllegalArgumentException("File " + name + " exceeds maximum size of 10MB");
                            }
                            if (totalUncompressedBytes > MAX_ZIP_EXPANDED_SIZE) {
                                throw new IllegalArgumentException("Total uncompressed ZIP size exceeds maximum limit of 200MB");
                            }
                            baos.write(buffer, 0, bytesRead);
                        }

                        byte[] fileData = baos.toByteArray();
                        String sha256 = getSha256(fileData);

                        origNameToSha.put(name, sha256);
                        origNameToSize.put(name, fileBytesCount);

                        if (shaToTempPath.containsKey(sha256)) {
                            skippedDuplicatesCount++;
                            continue; // exact duplicate skipped
                        }

                        // Save unique file named by SHA-256
                        String ext = "";
                        int lastDot = name.lastIndexOf('.');
                        if (lastDot != -1) {
                            ext = name.substring(lastDot);
                        }
                        Path uniqueFilePath = filesDir.resolve(sha256 + ext);
                        Files.write(uniqueFilePath, fileData);

                        shaToTempPath.put(sha256, uniqueFilePath.toString());
                    }
                }
            }

            // Parse Manifest JSON
            JsonNode manifestNode = objectMapper.readTree(manifestBytes);
            List<ScannedApplicationGroup> appPreviews = new ArrayList<>();
            Set<String> mappedShats = new HashSet<>();

            if (manifestNode.isArray()) {
                for (JsonNode node : manifestNode) {
                    String company = optString(node, "company", "companyName", "company_name", "employer");
                    String title = optString(node, "position", "jobTitle", "job_title", "title", "role");
                    String statusStr = optString(node, "status", "application_status", "state");
                    String priorityStr = optString(node, "priority", "importance");
                    String dateAppliedStr = optString(node, "dateApplied", "date_applied", "date");

                    if (company.isEmpty() || title.isEmpty()) {
                        continue;
                    }

                    // Apply terminal status cleanups for Dates
                    ApplicationStatus status = ApplicationStatus.APPLIED;
                    try {
                        status = ApplicationStatus.valueOf(statusStr.toUpperCase().replace(" ", "_"));
                    } catch (Exception ignored) {}

                    ApplicationPriority priority = ApplicationPriority.MEDIUM;
                    try {
                        priority = ApplicationPriority.valueOf(priorityStr.toUpperCase());
                    } catch (Exception ignored) {}

                    boolean isDuplicate = false;
                    Long existingId = null;
                    Optional<JobApplication> existingApp = jobApplicationRepository.findByCompanyNameIgnoreCaseAndJobTitleIgnoreCaseAndUserId(
                            company, title, currentUser.getId()
                    );
                    if (existingApp.isPresent()) {
                        isDuplicate = true;
                        existingId = existingApp.get().getId();
                    }

                    List<ScannedDocumentPreview> docPreviews = new ArrayList<>();
                    JsonNode filesNode = node.has("files") ? node.get("files") : (node.has("documents") ? node.get("documents") : node.get("attachments"));
                    if (filesNode != null && filesNode.isArray()) {
                        for (JsonNode fNode : filesNode) {
                            String manifestPath = optString(fNode, "name", "path", "fileName", "filename");
                            String docType = optString(fNode, "type", "documentType", "document_type");
                            long expectedSize = fNode.has("size") ? fNode.get("size").asLong() : -1;
                            String expectedSha = optString(fNode, "sha256", "hash");

                            String validationStatus = "MISSING";
                            String actualSha = origNameToSha.get(manifestPath);
                            long actualSize = origNameToSize.getOrDefault(manifestPath, -1L);
                            String tempDocId = UUID.randomUUID().toString();

                            if (actualSha != null) {
                                mappedShats.add(actualSha);
                                if (expectedSha.equalsIgnoreCase(actualSha) && (expectedSize == -1 || expectedSize == actualSize)) {
                                    validationStatus = "VALID";
                                } else {
                                    validationStatus = "CHANGED";
                                }
                            }

                            docPreviews.add(ScannedDocumentPreview.builder()
                                    .tempDocId(tempDocId)
                                    .fileName(manifestPath)
                                    .documentType(docType.toUpperCase())
                                    .fileSize(actualSize != -1 ? actualSize : expectedSize)
                                    .sha256(actualSha != null ? actualSha : expectedSha)
                                    .validationStatus(validationStatus)
                                    .build());
                        }
                    }

                    appPreviews.add(ScannedApplicationGroup.builder()
                            .tempAppId(UUID.randomUUID().toString())
                            .companyName(company)
                            .jobTitle(title)
                            .dateApplied(dateAppliedStr.isEmpty() ? null : dateAppliedStr)
                            .status(status.name())
                            .priority(priority.name())
                            .isDuplicate(isDuplicate)
                            .existingApplicationId(existingId)
                            .documents(docPreviews)
                            .build());
                }
            }

            // Identify unassigned unique files (not mapped in manifest)
            List<ScannedDocumentPreview> unassignedList = new ArrayList<>();
            for (Map.Entry<String, String> entry : origNameToSha.entrySet()) {
                String name = entry.getKey();
                String sha = entry.getValue();

                if (!mappedShats.contains(sha)) {
                    String tempDocId = UUID.randomUUID().toString();
                    long size = origNameToSize.getOrDefault(name, 0L);
                    unassignedList.add(ScannedDocumentPreview.builder()
                            .tempDocId(tempDocId)
                            .fileName(name)
                            .documentType("OTHER")
                            .fileSize(size)
                            .sha256(sha)
                            .validationStatus("VALID")
                            .build());
                }
            }

            // Write metadata mapping mapping files
            Map<String, Object> mappings = new HashMap<>();
            mappings.put("shaToTempPath", shaToTempPath);
            mappings.put("origNameToSha", origNameToSha);

            Map<String, String> docIdToSha = new HashMap<>();
            Map<String, String> docIdToOrigName = new HashMap<>();
            for (ScannedApplicationGroup group : appPreviews) {
                for (ScannedDocumentPreview doc : group.getDocuments()) {
                    docIdToSha.put(doc.getTempDocId(), doc.getSha256());
                    docIdToOrigName.put(doc.getTempDocId(), doc.getFileName());
                }
            }
            for (ScannedDocumentPreview doc : unassignedList) {
                docIdToSha.put(doc.getTempDocId(), doc.getSha256());
                docIdToOrigName.put(doc.getTempDocId(), doc.getFileName());
            }
            mappings.put("docIdToSha", docIdToSha);
            mappings.put("docIdToOrigName", docIdToOrigName);

            Files.write(tempDir.resolve("mappings.json"), objectMapper.writeValueAsBytes(mappings));

            // Persist the Scanned ImportBatch record in the database
            ImportBatch batch = ImportBatch.builder()
                    .id(scanId)
                    .user(currentUser)
                    .manifestHash(manifestHash)
                    .state("SCANNED")
                    .expiry(LocalDateTime.now().plusHours(2))
                    .build();
            importBatchRepository.save(batch);

            log.info("Batch scan {} complete. Skipped duplicates count: {}. Unassigned count: {}", scanId, skippedDuplicatesCount, unassignedList.size());

            return BulkScanResponse.builder()
                    .scanId(scanId)
                    .applications(appPreviews)
                    .unassignedFiles(unassignedList)
                    .build();

        } catch (Exception e) {
            log.error("Failed scanning bulk imports", e);
            cleanupTempDir(tempDir);
            throw new RuntimeException("Scan failed: " + e.getMessage(), e);
        }
    }

    @Transactional
    public BulkConfirmResponse confirm(BulkConfirmRequest request) {
        currentUserService.verifyNotDemo();
        User currentUser = currentUserService.getCurrentUser();

        // 1. Lock/claim import batch atomically matching scanId and userId
        ImportBatch batch = importBatchRepository.findByIdAndUserIdForUpdate(request.getScanId(), currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Import Batch not found with id: " + request.getScanId()));

        if ("PROCESSING".equals(batch.getState()) || "COMPLETED".equals(batch.getState())) {
            // Idempotent retry fallback success
            return BulkConfirmResponse.builder()
                    .successCount(request.getApplications().size())
                    .failureCount(0)
                    .documentFailures(new ArrayList<>())
                    .build();
        }

        // Validate double import protection
        Optional<ImportBatch> completed = importBatchRepository.findByUserIdAndManifestHashAndState(
                currentUser.getId(), batch.getManifestHash(), "COMPLETED"
        );
        if (completed.isPresent()) {
            throw new IllegalStateException("This manifest hash has already been imported.");
        }

        batch.setState("PROCESSING");
        importBatchRepository.saveAndFlush(batch);

        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "jobtrack-import-" + currentUser.getId() + "-" + request.getScanId());
        Path mappingsPath = tempDir.resolve("mappings.json");
        if (!Files.exists(mappingsPath)) {
            batch.setState("FAILED");
            importBatchRepository.save(batch);
            throw new IllegalStateException("Import mappings config has expired or is missing.");
        }

        List<BulkConfirmResponse.DocumentFailureInfo> docFailures = new ArrayList<>();
        List<String> uploadedStorageKeys = new ArrayList<>();

        int successCount = 0;
        int failureCount = 0;

        try {
            JsonNode mappingsNode = objectMapper.readTree(Files.readAllBytes(mappingsPath));
            Map<String, String> shaToTempPath = objectMapper.convertValue(mappingsNode.get("shaToTempPath"), Map.class);
            Map<String, String> docIdToSha = objectMapper.convertValue(mappingsNode.get("docIdToSha"), Map.class);
            Map<String, String> docIdToOrigName = objectMapper.convertValue(mappingsNode.get("docIdToOrigName"), Map.class);

            for (BulkConfirmApplication appReq : request.getApplications()) {
                try {
                    // Match existing applications (generic matching by company + title)
                    Optional<JobApplication> existingAppOpt = jobApplicationRepository.findByCompanyNameIgnoreCaseAndJobTitleIgnoreCaseAndUserId(
                            appReq.getCompanyName(), appReq.getJobTitle(), currentUser.getId()
                    );

                    JobApplication application;
                    ApplicationStatus newStatus = ApplicationStatus.APPLIED;
                    try {
                        newStatus = ApplicationStatus.valueOf(appReq.getStatus().toUpperCase());
                    } catch (Exception ignored) {}

                    ApplicationPriority newPriority = ApplicationPriority.MEDIUM;
                    try {
                        newPriority = ApplicationPriority.valueOf(appReq.getPriority().toUpperCase());
                    } catch (Exception ignored) {}

                    LocalDate dateApplied = null;
                    if (appReq.getDateApplied() != null && !appReq.getDateApplied().trim().isEmpty()) {
                        try {
                            dateApplied = LocalDate.parse(appReq.getDateApplied());
                        } catch (Exception ignored) {}
                    }

                    LocalDate followUpDate = null;
                    LocalDate deadlineDate = null;

                    // Reconcile or Create Application
                    if (existingAppOpt.isPresent()) {
                        application = existingAppOpt.get();
                        application.setStatus(newStatus);
                        application.setPriority(newPriority);
                        application.setDateApplied(dateApplied);
                        // Terminal date cleanups
                        if (newStatus == ApplicationStatus.REJECTED || newStatus == ApplicationStatus.WITHDRAWN || newStatus == ApplicationStatus.NO_RESPONSE) {
                            application.setFollowUpDate(null);
                            application.setDeadlineDate(null);
                        }
                        application = jobApplicationRepository.save(application);
                    } else {
                        application = JobApplication.builder()
                                .user(currentUser)
                                .companyName(appReq.getCompanyName())
                                .jobTitle(appReq.getJobTitle())
                                .status(newStatus)
                                .priority(newPriority)
                                .dateApplied(dateApplied)
                                .followUpDate(followUpDate)
                                .deadlineDate(deadlineDate)
                                .build();
                        application = jobApplicationRepository.save(application);

                        StatusHistory history = StatusHistory.builder()
                                .jobApplication(application)
                                .fromStatus(null)
                                .toStatus(newStatus)
                                .changedAt(LocalDateTime.now())
                                .note("Bulk imported application")
                                .build();
                        jobApplicationRepository.flush();
                    }

                    // Attach documents
                    if (appReq.getDocuments() != null) {
                        // Classify documents: exactly one primary CV and one primary COVER_LETTER
                        boolean cvAssigned = false;
                        boolean clAssigned = false;

                        for (BulkConfirmDocument docConfirm : appReq.getDocuments()) {
                            String sha = docIdToSha.get(docConfirm.getTempDocId());
                            String origName = docIdToOrigName.get(docConfirm.getTempDocId());
                            String tempFilePathStr = shaToTempPath.get(sha);

                            if (tempFilePathStr == null) {
                                docFailures.add(BulkConfirmResponse.DocumentFailureInfo.builder()
                                        .fileName(origName != null ? origName : "Unknown")
                                        .error("Extracted temporary document file not found.")
                                        .build());
                                continue;
                            }

                            Path tempFile = Paths.get(tempFilePathStr);
                            if (!Files.exists(tempFile)) {
                                docFailures.add(BulkConfirmResponse.DocumentFailureInfo.builder()
                                        .fileName(origName)
                                        .error("Temporary file missing on filesystem.")
                                        .build());
                                continue;
                            }

                            try {
                                byte[] fileBytes = Files.readAllBytes(tempFile);
                                String contentType = "application/octet-stream";
                                if (origName.toLowerCase().endsWith(".pdf")) {
                                    contentType = "application/pdf";
                                } else if (origName.toLowerCase().endsWith(".docx")) {
                                    contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                                }

                                CustomMultipartFile multipart = new CustomMultipartFile(fileBytes, "file", origName, contentType);

                                DocumentType targetType = DocumentType.OTHER;
                                try {
                                    targetType = DocumentType.valueOf(docConfirm.getDocumentType().toUpperCase());
                                } catch (Exception ignored) {}

                                // Reclassify logic: one primary CV and COVER_LETTER, extra become OTHER
                                if (targetType == DocumentType.CV) {
                                    if (cvAssigned) {
                                        targetType = DocumentType.OTHER;
                                    } else {
                                        cvAssigned = true;
                                    }
                                } else if (targetType == DocumentType.COVER_LETTER) {
                                    if (clAssigned) {
                                        targetType = DocumentType.OTHER;
                                    } else {
                                        clAssigned = true;
                                    }
                                }

                                // Upload and store document
                                ApplicationDocumentResponse stored = documentService.storeDocument(application.getId(), multipart, targetType);
                                // Read filePath reference key from DB to track for rolls compensation
                                ApplicationDocument savedDoc = documentRepository.findById(stored.getId()).orElseThrow();
                                uploadedStorageKeys.add(savedDoc.getFilePath());

                            } catch (Exception docEx) {
                                log.error("Failed uploading document: " + origName, docEx);
                                docFailures.add(BulkConfirmResponse.DocumentFailureInfo.builder()
                                        .fileName(origName)
                                        .error(docEx.getMessage())
                                        .build());
                            }
                        }
                    }
                    successCount++;
                } catch (Exception appEx) {
                    log.error("Failed importing application: " + appReq.getCompanyName(), appEx);
                    failureCount++;
                }
            }

            // Clean up mappings config file and unique temporary scan files
            cleanupTempDir(tempDir);

            batch.setState("COMPLETED");
            batch.setResultSummary(String.format("Imported %d applications. Failures: %d. Documents uploaded: %d.",
                    successCount, failureCount, uploadedStorageKeys.size()));
            importBatchRepository.save(batch);

            return BulkConfirmResponse.builder()
                    .successCount(successCount)
                    .failureCount(failureCount)
                    .documentFailures(docFailures)
                    .build();

        } catch (Exception ex) {
            log.error("Failed finalizing bulk import, initiating storage compensation", ex);
            // Storage Compensation for database rollback
            for (String key : uploadedStorageKeys) {
                try {
                    storageService.delete(key);
                } catch (Exception storageEx) {
                    log.error("Compensation failed to delete key: " + key, storageEx);
                }
            }
            batch.setState("FAILED");
            importBatchRepository.save(batch);
            throw new RuntimeException("Import confirmation failed: " + ex.getMessage(), ex);
        }
    }

    // 2-hour scheduled cleanup for abandoned scans
    @Scheduled(fixedRate = 600000) // Runs every 10 minutes
    public void cleanupExpiredScans() {
        LocalDateTime cutoff = LocalDateTime.now();
        List<ImportBatch> expiredBatches = importBatchRepository.findAll().stream()
                .filter(b -> b.getExpiry().isBefore(cutoff) && !"COMPLETED".equals(b.getState()))
                .collect(Collectors.toList());

        for (ImportBatch batch : expiredBatches) {
            log.info("Cleaning up expired import batch: {}", batch.getId());
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "jobtrack-import-" + batch.getUser().getId() + "-" + batch.getId());
            cleanupTempDir(tempDir);
            importBatchRepository.delete(batch);
        }
    }

    private void cleanupTempDir(Path tempDir) {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            log.info("Cleaned up temporary directory: {}", tempDir);
        } catch (Exception e) {
            log.error("Failed cleaning up directory: " + tempDir, e);
        }
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

    private String optString(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText().trim();
            }
        }
        return "";
    }
}
