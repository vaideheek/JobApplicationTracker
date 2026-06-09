package com.jobtrack.service;

import com.jobtrack.dto.JobApplicationRequest;
import com.jobtrack.dto.JobApplicationResponse;
import com.jobtrack.dto.StatusHistoryResponse;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.StatusHistory;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    @Transactional
    public JobApplicationResponse createApplication(JobApplicationRequest request) {
        JobApplication application = mapToEntity(request);
        application = jobApplicationRepository.save(application);

        // Create initial status history entry
        StatusHistory history = StatusHistory.builder()
                .jobApplication(application)
                .fromStatus(null)
                .toStatus(request.getStatus())
                .changedAt(LocalDateTime.now())
                .note("Application created")
                .build();
        statusHistoryRepository.save(history);

        return mapToResponse(application);
    }

    @Transactional
    public JobApplicationResponse updateApplication(Long id, JobApplicationRequest request) {
        JobApplication application = jobApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job Application", id));

        ApplicationStatus oldStatus = application.getStatus();

        // Update fields
        application.setCompanyName(request.getCompanyName());
        application.setJobTitle(request.getJobTitle());
        application.setLocation(request.getLocation());
        application.setJobUrl(request.getJobUrl());
        application.setDateApplied(request.getDateApplied());
        application.setStatus(request.getStatus());
        application.setStage(request.getStage());
        application.setRecruiterEmail(request.getRecruiterEmail());
        application.setNotes(request.getNotes());
        application.setSource(request.getSource());
        application.setCompanyCareerUrl(request.getCompanyCareerUrl());
        application.setSalaryRange(request.getSalaryRange());
        application.setPriority(request.getPriority());
        application.setFollowUpDate(request.getFollowUpDate());
        application.setDeadlineDate(request.getDeadlineDate());

        application = jobApplicationRepository.save(application);

        // Track status change if status changed
        if (oldStatus != request.getStatus()) {
            StatusHistory history = StatusHistory.builder()
                    .jobApplication(application)
                    .fromStatus(oldStatus)
                    .toStatus(request.getStatus())
                    .changedAt(LocalDateTime.now())
                    .note("Status changed from " + oldStatus + " to " + request.getStatus())
                    .build();
            statusHistoryRepository.save(history);
        }

        return mapToResponse(application);
    }

    @Transactional
    public void deleteApplication(Long id) {
        if (!jobApplicationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Job Application", id);
        }
        jobApplicationRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse getApplication(Long id) {
        JobApplication application = jobApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job Application", id));
        return mapToResponseWithHistory(application);
    }

    @Transactional(readOnly = true)
    public Page<JobApplicationResponse> getAllApplications(String search, ApplicationStatus status, Pageable pageable) {
        return jobApplicationRepository.findWithFilters(search, status, pageable)
                .map(this::mapToResponse);
    }

    // --- Mapping helpers ---

    private JobApplication mapToEntity(JobApplicationRequest request) {
        return JobApplication.builder()
                .companyName(request.getCompanyName())
                .jobTitle(request.getJobTitle())
                .location(request.getLocation())
                .jobUrl(request.getJobUrl())
                .dateApplied(request.getDateApplied())
                .status(request.getStatus())
                .stage(request.getStage())
                .recruiterEmail(request.getRecruiterEmail())
                .notes(request.getNotes())
                .source(request.getSource())
                .companyCareerUrl(request.getCompanyCareerUrl())
                .salaryRange(request.getSalaryRange())
                .priority(request.getPriority())
                .followUpDate(request.getFollowUpDate())
                .deadlineDate(request.getDeadlineDate())
                .build();
    }

    JobApplicationResponse mapToResponse(JobApplication entity) {
        return JobApplicationResponse.builder()
                .id(entity.getId())
                .companyName(entity.getCompanyName())
                .jobTitle(entity.getJobTitle())
                .location(entity.getLocation())
                .jobUrl(entity.getJobUrl())
                .dateApplied(entity.getDateApplied())
                .status(entity.getStatus())
                .stage(entity.getStage())
                .recruiterEmail(entity.getRecruiterEmail())
                .notes(entity.getNotes())
                .source(entity.getSource())
                .companyCareerUrl(entity.getCompanyCareerUrl())
                .salaryRange(entity.getSalaryRange())
                .priority(entity.getPriority())
                .followUpDate(entity.getFollowUpDate())
                .deadlineDate(entity.getDeadlineDate())
                .createdAt(entity.getCreatedAt())
                .lastUpdatedAt(entity.getLastUpdatedAt())
                .build();
    }

    JobApplicationResponse mapToResponseWithHistory(JobApplication entity) {
        JobApplicationResponse response = mapToResponse(entity);
        List<StatusHistoryResponse> history = statusHistoryRepository
                .findByJobApplicationIdOrderByChangedAtDesc(entity.getId())
                .stream()
                .map(h -> StatusHistoryResponse.builder()
                        .id(h.getId())
                        .fromStatus(h.getFromStatus())
                        .toStatus(h.getToStatus())
                        .changedAt(h.getChangedAt())
                        .note(h.getNote())
                        .build())
                .collect(Collectors.toList());
        response.setStatusHistory(history);
        return response;
    }
}
