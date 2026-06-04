package com.jobtrack.service;

import com.jobtrack.dto.DashboardStats;
import com.jobtrack.enums.ApplicationStatus;
import com.jobtrack.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JobApplicationRepository jobApplicationRepository;

    @Transactional(readOnly = true)
    public DashboardStats getStats() {
        long total = jobApplicationRepository.count();
        long interviews = jobApplicationRepository.countByStatus(ApplicationStatus.INTERVIEW);
        long offers = jobApplicationRepository.countByStatus(ApplicationStatus.OFFER);
        long rejections = jobApplicationRepository.countByStatus(ApplicationStatus.REJECTED);

        // Calculate start of current week (Monday)
        LocalDate startOfWeek = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long thisWeek = jobApplicationRepository.countByDateAppliedAfter(startOfWeek.minusDays(1));

        return DashboardStats.builder()
                .totalApplications(total)
                .interviews(interviews)
                .offers(offers)
                .rejections(rejections)
                .applicationsThisWeek(thisWeek)
                .build();
    }
}
