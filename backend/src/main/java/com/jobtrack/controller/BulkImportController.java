package com.jobtrack.controller;

import com.jobtrack.dto.*;
import com.jobtrack.service.BulkImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/bulk-import")
@RequiredArgsConstructor
public class BulkImportController {

    private final BulkImportService bulkImportService;

    @PostMapping("/scan")
    public ResponseEntity<BulkScanResponse> scan(
            @RequestParam("manifest") MultipartFile manifest,
            @RequestParam("laptopZip") MultipartFile laptopZip,
            @RequestParam("googleDriveZip") MultipartFile googleDriveZip
    ) {
        BulkScanResponse response = bulkImportService.scan(manifest, laptopZip, googleDriveZip);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<BulkConfirmResponse> confirm(@RequestBody BulkConfirmRequest request) {
        BulkConfirmResponse response = bulkImportService.confirm(request);
        return ResponseEntity.ok(response);
    }
}
