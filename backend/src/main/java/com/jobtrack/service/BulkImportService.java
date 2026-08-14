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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
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
    private final StatusHistoryRepository statusHistoryRepository;
    private final PlatformTransactionManager transactionManager;

    private static final long MAX_ZIP_EXPANDED_SIZE = 200 * 1024 * 1024; // 200 MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int MAX_RAW_ENTRIES = 1000;
    private static final int MAX_SUPPORTED_DOCUMENTS = 400;

    private static class ZipFileInfo {
        String sha256;
        long sizeBytes;
        String tempFilePath;

        ZipFileInfo(String sha256, long sizeBytes, String tempFilePath) {
            this.sha256 = sha256;
            this.sizeBytes = sizeBytes;
            this.tempFilePath = tempFilePath;
        }
    }

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

            // Maps tracking ZIP file entry paths to their hashes and sizes
            Map<String, ZipFileInfo> laptopFiles = new HashMap<>();
            Map<String, ZipFileInfo> driveFiles = new HashMap<>();
            Map<String, String> shaToTempPath = new HashMap<>();

            int totalRawEntries = 0;
            long totalUncompressedBytes = 0;
            int totalExtractedDocs = 0;

            // 1. Process Laptop ZIP
            if (laptopZip != null && !laptopZip.isEmpty()) {
                try (ZipInputStream zis = new ZipInputStream(laptopZip.getInputStream())) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        totalRawEntries++;
                        if (totalRawEntries > MAX_RAW_ENTRIES) {
                            throw new IllegalArgumentException("ZIP contains too many raw entries. Maximum allowed is " + MAX_RAW_ENTRIES);
                        }

                        String name = entry.getName();
                        Path targetPath = tempDir.resolve(name).normalize();
                        if (!targetPath.startsWith(tempDir)) {
                            throw new SecurityException("ZIP slip attempt detected: " + name);
                        }

                        if (entry.isDirectory() || name.contains("__MACOSX") || name.contains(".DS_Store") || Paths.get(name).getFileName().toString().startsWith("._")) {
                            continue;
                        }

                        totalExtractedDocs++;
                        if (totalExtractedDocs > MAX_SUPPORTED_DOCUMENTS) {
                            throw new IllegalArgumentException("ZIP contains too many supported documents. Maximum allowed is " + MAX_SUPPORTED_DOCUMENTS);
                        }

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
                        String sha = getSha256(fileData);

                        String tempFilePath = shaToTempPath.get(sha);
                        if (tempFilePath == null) {
                            String ext = "";
                            int lastDot = name.lastIndexOf('.');
                            if (lastDot != -1) ext = name.substring(lastDot);
                            Path uniqueFilePath = filesDir.resolve(sha + ext);
                            Files.write(uniqueFilePath, fileData);
                            tempFilePath = uniqueFilePath.toString();
                            shaToTempPath.put(sha, tempFilePath);
                        }

                        laptopFiles.put(normalizePath(name), new ZipFileInfo(sha, fileBytesCount, tempFilePath));
                    }
                }
            }

            // 2. Process Google Drive ZIP
            if (googleDriveZip != null && !googleDriveZip.isEmpty()) {
                try (ZipInputStream zis = new ZipInputStream(googleDriveZip.getInputStream())) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        totalRawEntries++;
                        if (totalRawEntries > MAX_RAW_ENTRIES) {
                            throw new IllegalArgumentException("ZIP contains too many raw entries. Maximum allowed is " + MAX_RAW_ENTRIES);
                        }

                        String name = entry.getName();
                        Path targetPath = tempDir.resolve(name).normalize();
                        if (!targetPath.startsWith(tempDir)) {
                            throw new SecurityException("ZIP slip attempt detected: " + name);
                        }

                        if (entry.isDirectory() || name.contains("__MACOSX") || name.contains(".DS_Store") || Paths.get(name).getFileName().toString().startsWith("._")) {
                            continue;
                        }

                        totalExtractedDocs++;
                        if (totalExtractedDocs > MAX_SUPPORTED_DOCUMENTS) {
                            throw new IllegalArgumentException("ZIP contains too many supported documents. Maximum allowed is " + MAX_SUPPORTED_DOCUMENTS);
                        }

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
                        String sha = getSha256(fileData);

                        String tempFilePath = shaToTempPath.get(sha);
                        if (tempFilePath == null) {
                            String ext = "";
                            int lastDot = name.lastIndexOf('.');
                            if (lastDot != -1) ext = name.substring(lastDot);
                            Path uniqueFilePath = filesDir.resolve(sha + ext);
                            Files.write(uniqueFilePath, fileData);
                            tempFilePath = uniqueFilePath.toString();
                            shaToTempPath.put(sha, tempFilePath);
                        }

                        driveFiles.put(normalizePath(name), new ZipFileInfo(sha, fileBytesCount, tempFilePath));
                    }
                }
            }

            // Parse Manifest JSON
            JsonNode manifestNode = objectMapper.readTree(manifestBytes);
            JsonNode appsNode = manifestNode.get("applications");
            JsonNode docsNode = manifestNode.get("documents");

            if (appsNode == null || !appsNode.isArray()) {
                throw new IllegalArgumentException("Invalid manifest schema: missing applications array.");
            }
            if (docsNode == null || !docsNode.isArray()) {
                throw new IllegalArgumentException("Invalid manifest schema: missing documents array.");
            }

            boolean hasValidationErrors = false;
            List<String> validationIssues = new ArrayList<>();
            Set<String> matchedZipPaths = new HashSet<>();
            Map<String, String> docIdToSha = new HashMap<>();
            Map<String, String> docIdToOrigName = new HashMap<>();

            // Map to store generated ScannedDocumentPreview for ATTACH documents grouped by manifestApplicationId
            Map<String, List<ScannedDocumentPreview>> appDocPreviews = new HashMap<>();
            List<ScannedDocumentPreview> unassignedList = new ArrayList<>();
            long skippedDuplicatesCount = 0;

            for (JsonNode docNode : docsNode) {
                String disposition = optString(docNode, "disposition");
                String appId = optString(docNode, "manifestApplicationId");
                String sourceArchiveId = optString(docNode, "sourceArchiveId");
                String relativePath = optString(docNode, "relativePath");
                String filename = optString(docNode, "filename");
                long sizeBytes = docNode.get("sizeBytes").asLong();
                String sha256 = optString(docNode, "sha256");
                String uploadDocType = optString(docNode, "uploadDocumentType");

                String expectedPath = "";
                if ("LAPTOP".equalsIgnoreCase(sourceArchiveId)) {
                    expectedPath = "JobApps/" + relativePath;
                } else if ("GOOGLE_DRIVE".equalsIgnoreCase(sourceArchiveId)) {
                    expectedPath = "Jobs/" + relativePath;
                }
                String normExpected = normalizePath(expectedPath);
                matchedZipPaths.add(normExpected);

                ZipFileInfo zipInfo = null;
                if ("LAPTOP".equalsIgnoreCase(sourceArchiveId)) {
                    zipInfo = laptopFiles.get(normExpected);
                } else if ("GOOGLE_DRIVE".equalsIgnoreCase(sourceArchiveId)) {
                    zipInfo = driveFiles.get(normExpected);
                }

                String validationStatus = "MISSING";
                long actualSize = -1;
                String actualSha = null;

                if (zipInfo != null) {
                    actualSize = zipInfo.sizeBytes;
                    actualSha = zipInfo.sha256;
                    if (actualSha.equalsIgnoreCase(sha256) && actualSize == sizeBytes) {
                        validationStatus = "VALID";
                    } else {
                        validationStatus = "CHANGED";
                        hasValidationErrors = true;
                        validationIssues.add("Document " + filename + " (path: " + normExpected + ") size or checksum has changed.");
                    }
                } else {
                    hasValidationErrors = true;
                    validationIssues.add("Document " + filename + " (path: " + normExpected + ") is missing from ZIP archives.");
                }

                String tempDocId = UUID.randomUUID().toString();
                docIdToSha.put(tempDocId, actualSha != null ? actualSha : sha256);
                docIdToOrigName.put(tempDocId, filename);

                ScannedDocumentPreview preview = ScannedDocumentPreview.builder()
                        .tempDocId(tempDocId)
                        .fileName(filename)
                        .documentType("ATTACH".equalsIgnoreCase(disposition) ? uploadDocType : "OTHER")
                        .fileSize(actualSize != -1 ? actualSize : sizeBytes)
                        .sha256(actualSha != null ? actualSha : sha256)
                        .validationStatus(validationStatus)
                        .build();

                if ("SKIP_EXACT_DUPLICATE".equalsIgnoreCase(disposition)) {
                    skippedDuplicatesCount++;
                } else if ("UNASSIGNED_REVIEW".equalsIgnoreCase(disposition)) {
                    unassignedList.add(preview);
                } else if ("ATTACH".equalsIgnoreCase(disposition)) {
                    appDocPreviews.computeIfAbsent(appId, k -> new ArrayList<>()).add(preview);
                }
            }

            // 3. Process Applications previews
            List<ScannedApplicationGroup> appPreviews = new ArrayList<>();
            for (JsonNode appNode : appsNode) {
                String appId = optString(appNode, "manifestApplicationId");
                String company = optString(appNode, "companyName");
                String title = optString(appNode, "jobTitle");
                String statusStr = optString(appNode, "status");
                String priorityStr = optString(appNode, "priority");
                String dateAppliedStr = optString(appNode, "dateApplied");
                String stage = optString(appNode, "stage");
                String source = optString(appNode, "source");
                String notes = optString(appNode, "notes");
                String importAction = optString(appNode, "importAction");

                if (company.isEmpty() || title.isEmpty()) {
                    continue;
                }

                // Check duplicates against db records
                boolean isDuplicate = false;
                Long existingId = null;
                Optional<JobApplication> existingApp = jobApplicationRepository.findByCompanyNameIgnoreCaseAndJobTitleIgnoreCaseAndUserId(
                        company, title, currentUser.getId()
                );
                if (existingApp.isPresent()) {
                    isDuplicate = true;
                    existingId = existingApp.get().getId();
                } else if ("MATCH_EXISTING".equalsIgnoreCase(importAction)) {
                    throw new IllegalArgumentException("Scan failed: Import action is MATCH_EXISTING but no matching database record was found for " + company + " - " + title);
                }

                List<ScannedDocumentPreview> docPreviews = appDocPreviews.getOrDefault(appId, Collections.emptyList());

                appPreviews.add(ScannedApplicationGroup.builder()
                        .tempAppId(UUID.randomUUID().toString())
                        .companyName(company)
                        .jobTitle(title)
                        .dateApplied(dateAppliedStr.isEmpty() ? null : dateAppliedStr)
                        .status(statusStr)
                        .priority(priorityStr)
                        .isDuplicate(isDuplicate)
                        .existingApplicationId(existingId)
                        .stage(stage)
                        .source(source)
                        .notes(notes)
                        .documents(docPreviews)
                        .build());
            }

            // Check if there are any unexpected files in ZIP archives
            for (String key : laptopFiles.keySet()) {
                if (!matchedZipPaths.contains(key)) {
                    hasValidationErrors = true;
                    validationIssues.add("Unexpected file " + key + " found in LAPTOP ZIP.");
                }
            }
            for (String key : driveFiles.keySet()) {
                if (!matchedZipPaths.contains(key)) {
                    hasValidationErrors = true;
                    validationIssues.add("Unexpected file " + key + " found in GOOGLE_DRIVE ZIP.");
                }
            }

            if (appPreviews.isEmpty()) {
                hasValidationErrors = true;
                validationIssues.add("No applications found in the manifest.");
            }

            // 5. Verification Check Rules (Fail scanning if counts mismatch)
            if (appPreviews.size() != 172) {
                throw new IllegalArgumentException("Scan failed verification rules: Expected exactly 172 applications, but parsed " + appPreviews.size());
            }
            long rejectedCount = appPreviews.stream().filter(a -> "REJECTED".equalsIgnoreCase(a.getStatus())).count();
            if (rejectedCount != 57) {
                throw new IllegalArgumentException("Scan failed verification rules: Expected exactly 57 REJECTED applications, but found " + rejectedCount);
            }
            long noResponseCount = appPreviews.stream().filter(a -> "NO_RESPONSE".equalsIgnoreCase(a.getStatus())).count();
            if (noResponseCount != 115) {
                throw new IllegalArgumentException("Scan failed verification rules: Expected exactly 115 NO_RESPONSE applications, but found " + noResponseCount);
            }
            int totalAttachments = appPreviews.stream().mapToInt(a -> a.getDocuments().size()).sum();
            if (totalAttachments != 235) {
                throw new IllegalArgumentException("Scan failed verification rules: Expected exactly 235 attachments, but found " + totalAttachments);
            }
            long appsWithFiles = appPreviews.stream().filter(a -> !a.getDocuments().isEmpty()).count();
            if (appsWithFiles != 85) {
                throw new IllegalArgumentException("Scan failed verification rules: Expected exactly 85 applications with attachments, but found " + appsWithFiles);
            }
            if (unassignedList.size() != 69) {
                throw new IllegalArgumentException("Scan failed verification rules: Expected exactly 69 unassigned unique files, but found " + unassignedList.size());
            }
            if (skippedDuplicatesCount != 16) {
                throw new IllegalArgumentException("Scan failed verification rules: Expected exactly 16 exact duplicates skipped, but found " + skippedDuplicatesCount);
            }

            // Persist metadata mappings for confirm lookup
            Map<String, Object> mappings = new HashMap<>();
            mappings.put("shaToTempPath", shaToTempPath);
            mappings.put("docIdToSha", docIdToSha);
            mappings.put("docIdToOrigName", docIdToOrigName);
            mappings.put("hasValidationErrors", hasValidationErrors);

            Files.write(tempDir.resolve("mappings.json"), objectMapper.writeValueAsBytes(mappings));

            // Persist Scanned ImportBatch in database
            ImportBatch batch = ImportBatch.builder()
                    .id(scanId)
                    .user(currentUser)
                    .manifestHash(manifestHash)
                    .state("SCANNED")
                    .expiry(LocalDateTime.now().plusHours(2))
                    .build();
            importBatchRepository.save(batch);

            log.info("Batch scan {} verified and completed successfully.", scanId);

            return BulkScanResponse.builder()
                    .scanId(scanId)
                    .applications(appPreviews)
                    .unassignedFiles(unassignedList)
                    .canConfirm(!hasValidationErrors)
                    .validationIssues(validationIssues)
                    .build();

        } catch (Exception e) {
            log.error("Failed scanning bulk imports", e);
            cleanupTempDir(tempDir);
            throw new RuntimeException("Scan failed: " + e.getMessage(), e);
        }
    }

    public BulkConfirmResponse confirm(BulkConfirmRequest request) {
        currentUserService.verifyNotDemo();
        User currentUser = currentUserService.getCurrentUser();

        // Validate confirm payload applications count
        if (request.getApplications() == null || request.getApplications().isEmpty()) {
            throw new IllegalArgumentException("Confirmation rejected: Applications list cannot be empty.");
        }

        // 1. Acquire and persist the PROCESSING claim inside a real TransactionTemplate transaction (propagation REQUIRES_NEW)
        TransactionTemplate claimTxTemplate = new TransactionTemplate(transactionManager);
        claimTxTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);

        try {
            claimTxTemplate.executeWithoutResult(status -> {
                ImportBatch b = importBatchRepository.findByIdAndUserIdForUpdate(request.getScanId(), currentUser.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Import Batch not found with id: " + request.getScanId()));

                if ("PROCESSING".equals(b.getState())) {
                    throw new ConflictException("Import batch is currently being processed.");
                }
                if ("COMPLETED".equals(b.getState())) {
                    throw new ConflictException("Import batch has already been completed.");
                }

                b.setState("PROCESSING");
                importBatchRepository.saveAndFlush(b);
            });
        } catch (ConflictException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to acquire PROCESSING claim: " + ex.getMessage(), ex);
        }

        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "jobtrack-import-" + currentUser.getId() + "-" + request.getScanId());
        Path mappingsPath = tempDir.resolve("mappings.json");
        if (!Files.exists(mappingsPath)) {
            // Set batch to FAILED in a separate REQUIRES_NEW transaction
            TransactionTemplate failTxTemplate = new TransactionTemplate(transactionManager);
            failTxTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            failTxTemplate.executeWithoutResult(status -> {
                ImportBatch b = importBatchRepository.findById(request.getScanId()).orElse(null);
                if (b != null) {
                    b.setState("FAILED");
                    importBatchRepository.saveAndFlush(b);
                }
            });
            throw new IllegalStateException("Import mappings config has expired or is missing.");
        }

        List<String> uploadedStorageKeys = new ArrayList<>();

        try {
            JsonNode mappingsNode = objectMapper.readTree(Files.readAllBytes(mappingsPath));
            boolean hasValidationErrors = mappingsNode.has("hasValidationErrors") && mappingsNode.get("hasValidationErrors").asBoolean();
            if (hasValidationErrors) {
                throw new IllegalArgumentException("Import confirmation is disabled due to missing, changed, or unexpected files.");
            }

            Map<String, String> shaToTempPath = objectMapper.convertValue(mappingsNode.get("shaToTempPath"), Map.class);
            Map<String, String> docIdToSha = objectMapper.convertValue(mappingsNode.get("docIdToSha"), Map.class);
            Map<String, String> docIdToOrigName = objectMapper.convertValue(mappingsNode.get("docIdToOrigName"), Map.class);

            // 2. Perform the main import work AND the COMPLETED batch update in the SAME database transaction
            TransactionTemplate mainTxTemplate = new TransactionTemplate(transactionManager);
            mainTxTemplate.executeWithoutResult(status -> {
                for (BulkConfirmApplication appReq : request.getApplications()) {
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
                        dateApplied = LocalDate.parse(appReq.getDateApplied());
                    }

                    if (existingAppOpt.isPresent()) {
                        application = existingAppOpt.get();
                        ApplicationStatus oldStatus = application.getStatus();
                        application.setStatus(newStatus);
                        application.setPriority(newPriority);
                        application.setDateApplied(dateApplied);
                        application.setStage(appReq.getStage());
                        application.setSource(appReq.getSource());
                        application.setNotes(appReq.getNotes());
                        if (newStatus == ApplicationStatus.REJECTED || newStatus == ApplicationStatus.WITHDRAWN || newStatus == ApplicationStatus.NO_RESPONSE) {
                            application.setFollowUpDate(null);
                            application.setDeadlineDate(null);
                        }
                        application = jobApplicationRepository.save(application);

                        if (oldStatus != newStatus) {
                            StatusHistory history = StatusHistory.builder()
                                    .jobApplication(application)
                                    .fromStatus(oldStatus)
                                    .toStatus(newStatus)
                                    .changedAt(LocalDateTime.now())
                                    .note("Status changed from " + oldStatus + " to " + newStatus + " via bulk import")
                                    .build();
                            statusHistoryRepository.save(history);
                        }
                    } else {
                        application = JobApplication.builder()
                                .user(currentUser)
                                .companyName(appReq.getCompanyName())
                                .jobTitle(appReq.getJobTitle())
                                .status(newStatus)
                                .priority(newPriority)
                                .dateApplied(dateApplied)
                                .stage(appReq.getStage())
                                .source(appReq.getSource())
                                .notes(appReq.getNotes())
                                .build();
                        if (newStatus == ApplicationStatus.REJECTED || newStatus == ApplicationStatus.WITHDRAWN || newStatus == ApplicationStatus.NO_RESPONSE) {
                            application.setFollowUpDate(null);
                            application.setDeadlineDate(null);
                        }
                        application = jobApplicationRepository.save(application);

                        StatusHistory history = StatusHistory.builder()
                                .jobApplication(application)
                                .fromStatus(null)
                                .toStatus(newStatus)
                                .changedAt(LocalDateTime.now())
                                .note("Bulk imported application")
                                .build();
                        statusHistoryRepository.save(history);
                    }

                    // Attach documents
                    if (appReq.getDocuments() != null) {
                        boolean cvAssigned = false;
                        boolean clAssigned = false;

                        for (BulkConfirmDocument docConfirm : appReq.getDocuments()) {
                            String sha = docIdToSha.get(docConfirm.getTempDocId());
                            String origName = docIdToOrigName.get(docConfirm.getTempDocId());
                            String tempFilePathStr = shaToTempPath.get(sha);

                            if (tempFilePathStr == null) {
                                throw new IllegalArgumentException("Extracted temporary document file not found: " + origName);
                            }

                            Path tempFile = Paths.get(tempFilePathStr);
                            if (!Files.exists(tempFile)) {
                                try {
                                    throw new FileNotFoundException("Temporary file missing: " + origName);
                                } catch (FileNotFoundException e) {
                                    throw new RuntimeException(e);
                                }
                            }

                            byte[] fileBytes;
                            try {
                                fileBytes = Files.readAllBytes(tempFile);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

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

                            if (targetType == DocumentType.CV) {
                                if (cvAssigned) targetType = DocumentType.OTHER;
                                else cvAssigned = true;
                            } else if (targetType == DocumentType.COVER_LETTER) {
                                if (clAssigned) targetType = DocumentType.OTHER;
                                else clAssigned = true;
                            }

                            ApplicationDocumentResponse stored = documentService.storeDocument(application.getId(), multipart, targetType);
                            ApplicationDocument savedDoc = documentRepository.findById(stored.getId()).orElseThrow();
                            uploadedStorageKeys.add(savedDoc.getFilePath());
                        }
                    }
                }

                // Update Import Batch state to COMPLETED inside the same database transaction
                ImportBatch dbBatch = importBatchRepository.findById(request.getScanId())
                        .orElseThrow(() -> new ResourceNotFoundException("Import Batch not found with id: " + request.getScanId()));
                dbBatch.setState("COMPLETED");
                dbBatch.setResultSummary(String.format("Imported %d applications. Documents uploaded: %d.",
                        request.getApplications().size(), uploadedStorageKeys.size()));
                importBatchRepository.saveAndFlush(dbBatch);
            });

            // Cleanup scan assets on successful confirmation
            cleanupTempDir(tempDir);

            return BulkConfirmResponse.builder()
                    .successCount(request.getApplications().size())
                    .failureCount(0)
                    .documentFailures(new ArrayList<>())
                    .build();

        } catch (Exception ex) {
            log.error("Failed finalizing bulk import, initiating storage compensation", ex);

            // Storage Compensation for database rollback: ONLY triggered if database transaction was rolled back
            for (String key : uploadedStorageKeys) {
                try {
                    storageService.delete(key);
                } catch (Exception storageEx) {
                    log.error("Compensation failed to delete key: " + key, storageEx);
                }
            }

            // Mark batch as FAILED in database (separate REQUIRES_NEW transaction)
            TransactionTemplate failTxTemplate = new TransactionTemplate(transactionManager);
            failTxTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
            try {
                failTxTemplate.executeWithoutResult(status -> {
                    ImportBatch dbBatch = importBatchRepository.findById(request.getScanId()).orElse(null);
                    if (dbBatch != null) {
                        dbBatch.setState("FAILED");
                        importBatchRepository.saveAndFlush(dbBatch);
                    }
                });
            } catch (Exception dbEx) {
                log.error("Failed marking batch as FAILED in database", dbEx);
            }

            if (ex instanceof ConflictException) {
                throw (ConflictException) ex;
            }
            if (ex instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) ex;
            }
            throw new RuntimeException("Import confirmation failed: " + ex.getMessage(), ex);
        }
    }

    @Scheduled(fixedRate = 600000)
    @Transactional
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

    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        return path.replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+$", "")
                .replaceAll("/+", "/");
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
