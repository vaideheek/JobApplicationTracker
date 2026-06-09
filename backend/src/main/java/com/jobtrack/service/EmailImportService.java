package com.jobtrack.service;

import com.jobtrack.dto.EmailParseRequest;
import com.jobtrack.dto.EmailParseResponse;
import com.jobtrack.dto.JobApplicationResponse;
import com.jobtrack.entity.JobApplication;
import com.jobtrack.entity.StatusHistory;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EmailImportService {

    private final JobApplicationRepository jobApplicationRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final EmailParser emailParser;
    private final JobApplicationService jobApplicationService;

    public EmailParseResponse parseEmail(EmailParseRequest request) {
        return emailParser.parse(request.getRawEmailText());
    }

    @Transactional
    public JobApplicationResponse confirmImport(EmailParseResponse request) {
        Optional<JobApplication> existingAppOpt = jobApplicationRepository
                .findByCompanyNameIgnoreCaseAndJobTitleIgnoreCase(request.getCompanyName(), request.getJobTitle());

        JobApplication application;
        ApplicationStatus oldStatus = null;
        boolean isNew = false;

        if (existingAppOpt.isPresent()) {
            application = existingAppOpt.get();
            oldStatus = application.getStatus();

            // Do not automatically overwrite existing important fields with empty parsed values.
            // Only update fields when the parsed/edited value is not null/blank.
            if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
                application.setCompanyName(request.getCompanyName());
            }
            if (request.getJobTitle() != null && !request.getJobTitle().isBlank()) {
                application.setJobTitle(request.getJobTitle());
            }
            
            // Status is always updated
            application.setStatus(request.getStatus());

            if (request.getStage() != null && !request.getStage().isBlank()) {
                application.setStage(request.getStage());
            }
            if (request.getRecruiterEmail() != null && !request.getRecruiterEmail().isBlank()) {
                application.setRecruiterEmail(request.getRecruiterEmail());
            }
            if (request.getSource() != null && !request.getSource().isBlank()) {
                application.setSource(request.getSource());
            }

            // Date mapping for existing application:
            // Use importantDate mainly as followUpDate for interviews/assessments.
            // For rejected/offers (or other statuses), do not change dateApplied on existing apps.
            if (request.getImportantDate() != null) {
                if (request.getStatus() == ApplicationStatus.INTERVIEW || request.getStatus() == ApplicationStatus.ASSESSMENT) {
                    application.setFollowUpDate(request.getImportantDate());
                }
            }

            // Merge suggested notes
            String newNotes = request.getSuggestedNotes();
            if (newNotes != null && !newNotes.isBlank()) {
                String currentNotes = application.getNotes();
                if (currentNotes == null || currentNotes.isBlank()) {
                    application.setNotes(newNotes);
                } else {
                    application.setNotes(currentNotes + "\n\n--- Imported Email Note [" + LocalDate.now() + "] ---\n" + newNotes);
                }
            }

            application = jobApplicationRepository.save(application);

            // Record status history entry
            StatusHistory history = StatusHistory.builder()
                    .jobApplication(application)
                    .fromStatus(oldStatus)
                    .toStatus(request.getStatus())
                    .changedAt(LocalDateTime.now())
                    .note(oldStatus != request.getStatus()
                            ? "Status updated via Email Import: " + request.getStage()
                            : "Email Import processed: " + request.getStage())
                    .build();
            statusHistoryRepository.save(history);

        } else {
            isNew = true;
            // Create new application
            application = new JobApplication();
            application.setCompanyName(request.getCompanyName());
            application.setJobTitle(request.getJobTitle());
            application.setStatus(request.getStatus());
            application.setStage(request.getStage());
            application.setRecruiterEmail(request.getRecruiterEmail());
            application.setSource(request.getSource() != null && !request.getSource().isBlank() ? request.getSource() : "Email");
            application.setNotes(request.getSuggestedNotes());

            // Date mapping for new application:
            // For new applications, use today as dateApplied unless the email clearly says "applied on"
            // Wait, we can assume if status is APPLIED or IN_REVIEW, and importantDate is set, it might be the application date.
            // For interviews/assessments, importantDate is the followUpDate, and dateApplied is today.
            if (request.getStatus() == ApplicationStatus.INTERVIEW || request.getStatus() == ApplicationStatus.ASSESSMENT) {
                application.setDateApplied(LocalDate.now());
                application.setFollowUpDate(request.getImportantDate());
            } else {
                application.setDateApplied(request.getImportantDate() != null ? request.getImportantDate() : LocalDate.now());
            }

            application = jobApplicationRepository.save(application);

            // Create initial status history entry
            StatusHistory history = StatusHistory.builder()
                    .jobApplication(application)
                    .fromStatus(null)
                    .toStatus(request.getStatus())
                    .changedAt(LocalDateTime.now())
                    .note("Application created via Email Import")
                    .build();
            statusHistoryRepository.save(history);
        }

        // Return mapped response with history
        return jobApplicationService.mapToResponseWithHistory(application);
    }
}
