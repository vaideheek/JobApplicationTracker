package com.jobtrack.service;

import com.jobtrack.dto.JobApplicationRequest;
import com.jobtrack.dto.JobApplicationResponse;
import com.jobtrack.dto.StatusHistoryResponse;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.StatusHistory;
import com.jobtrack.entity.User;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.exception.ResourceNotFoundException;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final ApplicationDocumentService applicationDocumentService;
    private final CurrentUserService currentUserService;

    @Transactional
    public JobApplicationResponse createApplication(JobApplicationRequest request) {
        currentUserService.verifyNotDemo();
        User user = currentUserService.getCurrentUser();

        JobApplication application = mapToEntity(request, user);
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
        currentUserService.verifyNotDemo();
        User user = currentUserService.getCurrentUser();

        JobApplication application = jobApplicationRepository.findByIdAndUserId(id, user.getId())
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
        if (request.getStatus() == ApplicationStatus.REJECTED
            || request.getStatus() == ApplicationStatus.WITHDRAWN
            || request.getStatus() == ApplicationStatus.NO_RESPONSE) {
            application.setFollowUpDate(null);
            application.setDeadlineDate(null);
        } else {
            application.setFollowUpDate(request.getFollowUpDate());
            application.setDeadlineDate(request.getDeadlineDate());
        }
        application.setJobDescription(request.getJobDescription());
        application.setJobDescriptionSummary(request.getJobDescriptionSummary());
        application.setOriginalJobUrl(request.getOriginalJobUrl());
        application.setMatchScore(request.getMatchScore());
        application.setMatchedSkills(request.getMatchedSkills());
        application.setMissingSkills(request.getMissingSkills());

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
        currentUserService.verifyNotDemo();
        User user = currentUserService.getCurrentUser();

        if (!jobApplicationRepository.existsByIdAndUserId(id, user.getId())) {
            throw new ResourceNotFoundException("Job Application", id);
        }

        // Documents ownership check is done internally in deleteApplicationDocuments
        applicationDocumentService.deleteApplicationDocuments(id);
        jobApplicationRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse getApplication(Long id) {
        User user = currentUserService.getCurrentUser();
        JobApplication application = jobApplicationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Job Application", id));
        return mapToResponseWithHistory(application);
    }

    @Transactional(readOnly = true)
    public Page<JobApplicationResponse> getAllApplications(
            String search,
            ApplicationStatus status,
            ApplicationPriority priority,
            LocalDate dateFrom,
            LocalDate dateTo,
            String documentState,
            Pageable pageable) {
        User user = currentUserService.getCurrentUser();
        return jobApplicationRepository.findWithFilters(
                search,
                status,
                priority,
                dateFrom,
                dateTo,
                documentState,
                user.getId(),
                pageable
        ).map(this::mapToResponse);
    }

    // --- Mapping helpers ---

    private JobApplication mapToEntity(JobApplicationRequest request, User user) {
        LocalDate followUp = request.getFollowUpDate();
        LocalDate deadline = request.getDeadlineDate();
        if (request.getStatus() == ApplicationStatus.REJECTED
            || request.getStatus() == ApplicationStatus.WITHDRAWN
            || request.getStatus() == ApplicationStatus.NO_RESPONSE) {
            followUp = null;
            deadline = null;
        }

        return JobApplication.builder()
                .user(user)
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
                .followUpDate(followUp)
                .deadlineDate(deadline)
                .jobDescription(request.getJobDescription())
                .jobDescriptionSummary(request.getJobDescriptionSummary())
                .originalJobUrl(request.getOriginalJobUrl())
                .matchScore(request.getMatchScore())
                .matchedSkills(request.getMatchedSkills())
                .missingSkills(request.getMissingSkills())
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
                .jobDescription(entity.getJobDescription())
                .jobDescriptionSummary(entity.getJobDescriptionSummary())
                .originalJobUrl(entity.getOriginalJobUrl())
                .matchScore(entity.getMatchScore())
                .matchedSkills(entity.getMatchedSkills())
                .missingSkills(entity.getMissingSkills())
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
