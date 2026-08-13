package com.jobtrack.service;

import com.jobtrack.dto.JobApplicationRequest;
import com.jobtrack.dto.EmailParseResponse;
import com.jobtrack.dto.PrioritySuggestionResponse;
import com.jobtrack.enums.ApplicationPriority;
import com.jobtrack.enums.ApplicationStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class PrioritySuggestionService {

    private static final List<String> TARGET_COMPANIES = Arrays.asList(
            "Google", "Microsoft", "Amazon", "Meta", "Apple", "Arm", "JetBrains", "Deloitte", "Accenture", "SAP",
            "Nokia", "Revolut", "Wise", "Spotify", "Booking.com", "Ericsson", "IBM", "Oracle", "Bloomberg",
            "JPMorgan", "Goldman Sachs", "Morgan Stanley"
    );

    public PrioritySuggestionResponse suggestPriority(JobApplicationRequest request) {
        return calculateSuggestion(
                request.getCompanyName(),
                request.getStatus(),
                request.getSource(),
                request.getSalaryRange(),
                request.getFollowUpDate(),
                request.getDeadlineDate()
        );
    }

    public PrioritySuggestionResponse suggestPriorityFromEmailImport(EmailParseResponse response) {
        // Map importantDate to followUpDate if status is INTERVIEW or ASSESSMENT
        LocalDate followUpDate = null;
        LocalDate deadlineDate = null;
        if (response.getStatus() == ApplicationStatus.INTERVIEW || response.getStatus() == ApplicationStatus.ASSESSMENT) {
            followUpDate = response.getImportantDate();
        } else {
            // For other statuses, we can still check date for general rules evaluation
            followUpDate = response.getImportantDate();
        }

        return calculateSuggestion(
                response.getCompanyName(),
                response.getStatus(),
                response.getSource(),
                null, // Email import doesn't extract salaryRange yet
                followUpDate,
                deadlineDate
        );
    }

    private PrioritySuggestionResponse calculateSuggestion(
            String companyName,
            ApplicationStatus status,
            String source,
            String salaryRange,
            LocalDate followUpDate,
            LocalDate deadlineDate
    ) {
        // Absolute Override Rule: REJECTED, WITHDRAWN or NO_RESPONSE -> LOW
        if (status == ApplicationStatus.REJECTED || status == ApplicationStatus.WITHDRAWN || status == ApplicationStatus.NO_RESPONSE) {
            String statusLabel;
            if (status == ApplicationStatus.REJECTED) {
                statusLabel = "rejected";
            } else if (status == ApplicationStatus.WITHDRAWN) {
                statusLabel = "withdrawn";
            } else {
                statusLabel = "no response received";
            }
            return PrioritySuggestionResponse.builder()
                    .priority(ApplicationPriority.LOW)
                    .explanation("application is " + statusLabel)
                    .build();
        }

        List<String> highReasons = new ArrayList<>();
        List<String> mediumReasons = new ArrayList<>();

        // 1. Offer -> HIGH
        if (status == ApplicationStatus.OFFER) {
            highReasons.add("job offer received");
        }

        // 2. Interview or Assessment -> HIGH
        if (status == ApplicationStatus.INTERVIEW || status == ApplicationStatus.ASSESSMENT) {
            String label = status == ApplicationStatus.INTERVIEW ? "interview scheduled" : "assessment pending";
            highReasons.add(label);
        }

        // 3. followUpDate within 7 days -> HIGH
        if (isWithinSevenDays(followUpDate)) {
            highReasons.add("upcoming follow-up date");
        }

        // 4. deadlineDate within 7 days -> HIGH
        if (isWithinSevenDays(deadlineDate)) {
            highReasons.add("application deadline soon");
        }

        // 5. source contains Referral -> HIGH
        if (source != null && source.toLowerCase().contains("referral")) {
            highReasons.add("referred candidate");
        }

        // 6. companyName matches target companies -> HIGH
        if (isTargetCompany(companyName)) {
            highReasons.add("target company (" + companyName + ")");
        }

        // 7. salaryRange is present -> MEDIUM minimum
        if (salaryRange != null && !salaryRange.isBlank()) {
            mediumReasons.add("salary range specified");
        }

        // Default -> MEDIUM
        mediumReasons.add("default baseline");

        // Precedence logic
        if (!highReasons.isEmpty()) {
            return PrioritySuggestionResponse.builder()
                    .priority(ApplicationPriority.HIGH)
                    .explanation(String.join(", ", highReasons))
                    .build();
        } else {
            // Filter out default baseline if salary range is also present to keep clean reasons
            if (mediumReasons.size() > 1) {
                mediumReasons.remove("default baseline");
            }
            return PrioritySuggestionResponse.builder()
                    .priority(ApplicationPriority.MEDIUM)
                    .explanation(String.join(", ", mediumReasons))
                    .build();
        }
    }

    private boolean isWithinSevenDays(LocalDate date) {
        if (date == null) return false;
        LocalDate today = LocalDate.now();
        LocalDate sevenDaysFromNow = today.plusDays(7);
        return !date.isBefore(today) && !date.isAfter(sevenDaysFromNow);
    }

    private boolean isTargetCompany(String name) {
        if (name == null || name.isBlank()) return false;
        String cleanName = name.trim().toLowerCase();
        for (String target : TARGET_COMPANIES) {
            if (target.toLowerCase().equals(cleanName)) {
                return true;
            }
        }
        return false;
    }
}
