package com.jobtrack.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileValidator {

    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L; // 10MB
    private static final int MAX_ZIP_ENTRIES = 200;
    private static final long MAX_UNCOMPRESSED_ENTRY_SIZE = 20L * 1024L * 1024L; // 20MB
    private static final double MAX_ZIP_COMPRESSION_RATIO = 100.0;

    public static void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds the maximum limit of 10MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("File must have a valid filename");
        }

        String lowercaseName = originalFilename.toLowerCase();
        if (!lowercaseName.endsWith(".pdf") && !lowercaseName.endsWith(".docx")) {
            throw new IllegalArgumentException("Unsupported file type. Only PDF and DOCX are allowed.");
        }

        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[4];
            int read = is.read(header);
            if (read < 4) {
                throw new IllegalArgumentException("File content is too small to be valid");
            }

            if (lowercaseName.endsWith(".pdf")) {
                // PDF magic bytes: %PDF (0x25, 0x50, 0x44, 0x46)
                if (header[0] != 0x25 || header[1] != 0x50 || header[2] != 0x44 || header[3] != 0x46) {
                    throw new IllegalArgumentException("Invalid PDF file structure: signature check failed");
                }
            } else {
                // DOCX is a ZIP format: PK.. (0x50, 0x4B, 0x03, 0x04)
                if (header[0] != 0x50 || header[1] != 0x4B || (header[2] != 0x03 && header[2] != 0x05 && header[2] != 0x07) || (header[3] != 0x04 && header[3] != 0x06 && header[3] != 0x08)) {
                    throw new IllegalArgumentException("Invalid DOCX file: signature check failed (not a valid ZIP archive)");
                }

                // Verify internal files and guard against zip bombs
                boolean hasContentTypes = false;
                boolean hasWordDocument = false;
                int entriesCount = 0;

                try (ZipInputStream zipStream = new ZipInputStream(file.getInputStream())) {
                    ZipEntry entry;
                    while ((entry = zipStream.getNextEntry()) != null) {
                        entriesCount++;
                        if (entriesCount > MAX_ZIP_ENTRIES) {
                            throw new IllegalArgumentException("Invalid DOCX zip archive: too many files (potential Zip Bomb)");
                        }

                        long size = entry.getSize();
                        long compressedSize = entry.getCompressedSize();

                        if (size > MAX_UNCOMPRESSED_ENTRY_SIZE) {
                            throw new IllegalArgumentException("Invalid DOCX zip archive: entry size exceeds safe limit (potential Zip Bomb)");
                        }

                        if (compressedSize > 0 && size > 1024) {
                            double ratio = (double) size / compressedSize;
                            if (ratio > MAX_ZIP_COMPRESSION_RATIO) {
                                throw new IllegalArgumentException("Invalid DOCX zip archive: compression ratio is too high (potential Zip Bomb)");
                            }
                        }

                        String name = entry.getName();
                        if ("[Content_Types].xml".equals(name)) {
                            hasContentTypes = true;
                        }
                        if ("word/document.xml".equals(name)) {
                            hasWordDocument = true;
                        }

                        zipStream.closeEntry();
                    }
                } catch (IllegalArgumentException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid DOCX format: failed to extract zip archive structure", e);
                }

                if (!hasContentTypes || !hasWordDocument) {
                    throw new IllegalArgumentException("Invalid DOCX file: missing standard Word Document internal components");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file content for validation", e);
        }
    }

    public static String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "document";
        }
        // Remove path traversal sequences and keep alphanumeric, dashes, dots, and underscores
        String sanitized = filename.replaceAll("[/\\\\\\s:]", "_").replaceAll("[^a-zA-Z0-9.-]", "_");
        if (sanitized.length() > 100) {
            int extIndex = sanitized.lastIndexOf('.');
            if (extIndex > 0) {
                String ext = sanitized.substring(extIndex);
                sanitized = sanitized.substring(0, 100 - ext.length()) + ext;
            } else {
                sanitized = sanitized.substring(0, 100);
            }
        }
        return sanitized;
    }
}
