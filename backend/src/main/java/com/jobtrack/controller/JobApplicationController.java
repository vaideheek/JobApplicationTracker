package com.jobtrack.controller;

import com.jobtrack.dto.JobApplicationRequest;
import com.jobtrack.dto.JobApplicationResponse;
import com.jobtrack.dto.PrioritySuggestionResponse;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.service.JobApplicationService;
import com.jobtrack.service.PrioritySuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;
    private final PrioritySuggestionService prioritySuggestionService;

    @GetMapping
    public ResponseEntity<Page<JobApplicationResponse>> getAllApplications(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) ApplicationPriority priority,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String documentState,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "lastUpdatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        // Validate sortBy against strict allowlist
        if (!sortBy.equals("lastUpdatedAt") && !sortBy.equals("dateApplied") && !sortBy.equals("createdAt") && !sortBy.equals("companyName")) {
            throw new IllegalArgumentException("Unsupported sort field: " + sortBy);
        }

        // Validate sortDir
        if (!sortDir.equalsIgnoreCase("asc") && !sortDir.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("Unsupported sort direction: " + sortDir);
        }

        // Validate size against strict supported values (15, 25, 50, 100)
        if (size != 15 && size != 25 && size != 50 && size != 100) {
            throw new IllegalArgumentException("Unsupported page size: " + size);
        }

        // Normalize negative page numbers
        if (page < 0) {
            page = 0;
        }

        // Setup deterministic secondary sort (id DESC)
        Sort.Order primaryOrder = sortDir.equalsIgnoreCase("asc")
                ? Sort.Order.asc(sortBy).nullsLast()
                : Sort.Order.desc(sortBy).nullsLast();
        Sort.Order secondaryOrder = Sort.Order.desc("id");
        Sort sort = Sort.by(primaryOrder, secondaryOrder);

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<JobApplicationResponse> applications = jobApplicationService
                .getAllApplications(search, status, priority, dateFrom, dateTo, documentState, pageable);
        return ResponseEntity.ok(applications);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobApplicationResponse> getApplication(@PathVariable Long id) {
        return ResponseEntity.ok(jobApplicationService.getApplication(id));
    }

    @PostMapping
    public ResponseEntity<JobApplicationResponse> createApplication(
            @Valid @RequestBody JobApplicationRequest request) {
        JobApplicationResponse created = jobApplicationService.createApplication(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobApplicationResponse> updateApplication(
            @PathVariable Long id,
            @Valid @RequestBody JobApplicationRequest request) {
        return ResponseEntity.ok(jobApplicationService.updateApplication(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable Long id) {
        jobApplicationService.deleteApplication(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/suggest-priority")
    public ResponseEntity<PrioritySuggestionResponse> suggestPriority(
            @RequestBody JobApplicationRequest request) {
        return ResponseEntity.ok(prioritySuggestionService.suggestPriority(request));
    }
}
