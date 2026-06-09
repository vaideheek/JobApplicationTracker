package com.jobtrack.controller;

import com.jobtrack.dto.EmailParseRequest;
import com.jobtrack.dto.EmailParseResponse;
import com.jobtrack.dto.JobApplicationResponse;
import com.jobtrack.service.EmailImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email-import")
@RequiredArgsConstructor
public class EmailImportController {

    private final EmailImportService emailImportService;

    @PostMapping("/parse")
    public ResponseEntity<EmailParseResponse> parseEmail(@Valid @RequestBody EmailParseRequest request) {
        EmailParseResponse response = emailImportService.parseEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<JobApplicationResponse> confirmImport(@RequestBody EmailParseResponse request) {
        JobApplicationResponse response = emailImportService.confirmImport(request);
        return ResponseEntity.ok(response);
    }
}
